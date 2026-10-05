package com.xldev.happytalky.core

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import java.util.UUID

object ProximityBleProtocol {
    val serviceUuid: ParcelUuid =
        ParcelUuid(
            UUID.fromString(
                "8f0f4d6a-72c4-4d9d-9b9b-6f0a7f4d6c21"
            )
        )

    const val SESSION_TIMEOUT_MS = 60_000L
    const val SIGNAL_STALE_MS = 3_500L
}

data class ProximityAdvertiseResult(
    val started: Boolean,
    val error: String? = null
)

class BleProximityScanner(
    context: Context,
    private val onReading:
        (ProximityReading?) -> Unit,
    private val onError:
        (String) -> Unit
) {
    private val appContext =
        context.applicationContext
    private val mainHandler =
        Handler(Looper.getMainLooper())
    private val processor =
        ProximitySignalProcessor()

    private var scanner: BluetoothLeScanner? = null
    private var running = false
    private var sessionBytes: ByteArray? = null
    private var lastSeenAt = 0L

    private val sessionTimeout =
        Runnable {
            if (running) {
                stop()
                onError(
                    "Nearby search timed out · try again"
                )
            }
        }

    private val staleCheck =
        object : Runnable {
            override fun run() {
                if (!running) {
                    return
                }

                val now =
                    SystemClock.elapsedRealtime()
                if (
                    lastSeenAt > 0L &&
                    now - lastSeenAt >=
                        ProximityBleProtocol
                            .SIGNAL_STALE_MS
                ) {
                    lastSeenAt = 0L
                    processor.reset()
                    onReading(null)
                }

                mainHandler.postDelayed(
                    this,
                    1_000L
                )
            }
        }

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                handleResult(result)
            }

            override fun onBatchScanResults(
                results: MutableList<ScanResult>
            ) {
                results.forEach(::handleResult)
            }

            override fun onScanFailed(
                errorCode: Int
            ) {
                running = false
                mainHandler.removeCallbacks(
                    staleCheck
                )
                mainHandler.removeCallbacks(
                    sessionTimeout
                )
                onError(
                    "Bluetooth scan failed " +
                        "(code $errorCode)"
                )
            }
        }

    fun start(token: String): Boolean {
        if (running) {
            return true
        }

        val expected =
            ProximitySessionToken.decode(
                token
            )
                ?: run {
                    onError(
                        "Invalid nearby-search session"
                    )
                    return false
                }

        if (
            Build.VERSION.SDK_INT >= 31 &&
            appContext.checkSelfPermission(
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onError(
                "Nearby devices permission is required"
            )
            return false
        }

        val manager =
            appContext.getSystemService(
                BluetoothManager::class.java
            )
        val foundScanner =
            runCatching {
                manager
                    ?.adapter
                    ?.bluetoothLeScanner
            }.getOrNull()
                ?: run {
                    onError(
                        "Turn on Bluetooth to continue nearby finding"
                    )
                    return false
                }

        val filter =
            ScanFilter.Builder()
                .setServiceData(
                    ProximityBleProtocol.serviceUuid,
                    expected
                )
                .build()
        val settings =
            ScanSettings.Builder()
                .setScanMode(
                    ScanSettings
                        .SCAN_MODE_LOW_LATENCY
                )
                .build()

        return try {
            processor.reset()
            lastSeenAt = 0L
            sessionBytes = expected
            scanner = foundScanner
            running = true
            foundScanner.startScan(
                listOf(filter),
                settings,
                scanCallback
            )
            mainHandler.post(
                staleCheck
            )
            mainHandler.postDelayed(
                sessionTimeout,
                ProximityBleProtocol
                    .SESSION_TIMEOUT_MS
            )
            true
        } catch (_: SecurityException) {
            running = false
            scanner = null
            sessionBytes = null
            onError(
                "Nearby devices permission is required"
            )
            false
        } catch (_: Exception) {
            running = false
            scanner = null
            sessionBytes = null
            onError(
                "Could not start Bluetooth search"
            )
            false
        }
    }

    fun stop() {
        val currentScanner =
            scanner
        running = false
        scanner = null
        sessionBytes = null
        lastSeenAt = 0L
        processor.reset()
        mainHandler.removeCallbacks(
            staleCheck
        )
        mainHandler.removeCallbacks(
            sessionTimeout
        )

        if (currentScanner != null) {
            runCatching {
                currentScanner.stopScan(
                    scanCallback
                )
            }
        }
    }

    private fun handleResult(
        result: ScanResult
    ) {
        if (!running) {
            return
        }

        val expected =
            sessionBytes
                ?: return
        val advertised =
            result.scanRecord
                ?.getServiceData(
                    ProximityBleProtocol
                        .serviceUuid
                )
                ?: return

        if (!advertised.contentEquals(expected)) {
            return
        }

        val now =
            SystemClock.elapsedRealtime()
        lastSeenAt = now
        val reading =
            processor.add(
                result.rssi,
                now
            )

        mainHandler.post {
            if (running) {
                onReading(reading)
            }
        }
    }
}

object BleProximityAdvertiser {
    private val mainHandler =
        Handler(Looper.getMainLooper())

    private var advertiser:
        BluetoothLeAdvertiser? = null
    private var advertiseCallback:
        AdvertiseCallback? = null
    private var currentToken: String? = null
    private var timeout: Runnable? = null

    @Synchronized
    fun start(
        context: Context,
        token: String,
        onResult:
            (ProximityAdvertiseResult) -> Unit
    ) {
        stop()

        val payload =
            ProximitySessionToken.decode(
                token
            )
                ?: run {
                    onResult(
                        ProximityAdvertiseResult(
                            started = false,
                            error =
                                "Invalid nearby-search session"
                        )
                    )
                    return
                }

        val appContext =
            context.applicationContext
        val endpointLabel =
            if (
                EndpointRole.fromContext(
                    appContext
                ) == EndpointRole.PHONE
            ) {
                "Phone"
            } else {
                "Watch"
            }

        if (
            Build.VERSION.SDK_INT >= 31 &&
            appContext.checkSelfPermission(
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onResult(
                ProximityAdvertiseResult(
                    started = false,
                    error =
                        "$endpointLabel needs Nearby devices permission"
                )
            )
            return
        }

        val manager =
            appContext.getSystemService(
                BluetoothManager::class.java
            )
        val foundAdvertiser =
            runCatching {
                manager
                    ?.adapter
                    ?.bluetoothLeAdvertiser
            }.getOrNull()
                ?: run {
                    onResult(
                        ProximityAdvertiseResult(
                            started = false,
                            error =
                                "$endpointLabel Bluetooth advertising unavailable"
                        )
                    )
                    return
                }

        val settings =
            AdvertiseSettings.Builder()
                .setAdvertiseMode(
                    AdvertiseSettings
                        .ADVERTISE_MODE_LOW_LATENCY
                )
                .setTxPowerLevel(
                    AdvertiseSettings
                        .ADVERTISE_TX_POWER_HIGH
                )
                .setConnectable(false)
                .setTimeout(
                    ProximityBleProtocol
                        .SESSION_TIMEOUT_MS
                        .toInt()
                )
                .build()

        val data =
            AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceData(
                    ProximityBleProtocol.serviceUuid,
                    payload
                )
                .build()

        val callback =
            object : AdvertiseCallback() {
                override fun onStartSuccess(
                    settingsInEffect:
                        AdvertiseSettings
                ) {
                    onResult(
                        ProximityAdvertiseResult(
                            started = true
                        )
                    )
                }

                override fun onStartFailure(
                    errorCode: Int
                ) {
                    synchronized(
                        BleProximityAdvertiser
                    ) {
                        if (
                            advertiseCallback ===
                                this
                        ) {
                            clearState()
                        }
                    }
                    onResult(
                        ProximityAdvertiseResult(
                            started = false,
                            error =
                                advertiseError(
                                    errorCode,
                                    endpointLabel
                                )
                        )
                    )
                }
            }

        advertiser = foundAdvertiser
        advertiseCallback = callback
        currentToken = token

        timeout =
            Runnable {
                stop(token)
            }.also {
                mainHandler.postDelayed(
                    it,
                    ProximityBleProtocol
                        .SESSION_TIMEOUT_MS
                )
            }

        try {
            foundAdvertiser.startAdvertising(
                settings,
                data,
                callback
            )
        } catch (_: SecurityException) {
            clearState()
            onResult(
                ProximityAdvertiseResult(
                    started = false,
                    error =
                        "$endpointLabel needs Nearby devices permission"
                )
            )
        } catch (_: Exception) {
            clearState()
            onResult(
                ProximityAdvertiseResult(
                    started = false,
                    error =
                        "Could not start $endpointLabel Bluetooth beacon"
                )
            )
        }
    }

    @Synchronized
    fun stop(
        token: String? = null
    ) {
        if (
            token != null &&
            token != currentToken
        ) {
            return
        }

        val currentAdvertiser =
            advertiser
        val currentCallback =
            advertiseCallback

        timeout?.let(
            mainHandler::removeCallbacks
        )

        if (
            currentAdvertiser != null &&
            currentCallback != null
        ) {
            runCatching {
                currentAdvertiser
                    .stopAdvertising(
                        currentCallback
                    )
            }
        }

        clearState()
    }

    private fun clearState() {
        timeout?.let(
            mainHandler::removeCallbacks
        )
        timeout = null
        advertiseCallback = null
        advertiser = null
        currentToken = null
    }

    private fun advertiseError(
        code: Int,
        endpointLabel: String
    ): String =
        when (code) {
            AdvertiseCallback
                .ADVERTISE_FAILED_DATA_TOO_LARGE ->
                "BLE payload is too large"

            AdvertiseCallback
                .ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ->
                "$endpointLabel Bluetooth is busy"

            AdvertiseCallback
                .ADVERTISE_FAILED_ALREADY_STARTED ->
                "$endpointLabel Bluetooth beacon is already active"

            AdvertiseCallback
                .ADVERTISE_FAILED_FEATURE_UNSUPPORTED ->
                "$endpointLabel does not support BLE advertising"

            else ->
                "$endpointLabel Bluetooth advertising failed " +
                    "(code $code)"
        }
}
