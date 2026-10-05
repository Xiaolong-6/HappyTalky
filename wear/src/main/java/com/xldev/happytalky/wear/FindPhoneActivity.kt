package com.xldev.happytalky.wear

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.xldev.happytalky.core.BleProximityScanner
import com.xldev.happytalky.core.DataLayerTransport
import com.xldev.happytalky.core.EndpointRole
import com.xldev.happytalky.core.PeerInfoStore
import com.xldev.happytalky.core.ProximityBand
import com.xldev.happytalky.core.ProximityReading
import com.xldev.happytalky.core.ProximitySessionToken
import com.xldev.happytalky.core.ProximityTrend
import com.xldev.happytalky.core.Protocol
import com.xldev.happytalky.core.StateStore
import kotlin.math.roundToInt

data class FindPhoneUiState(
    val phoneReady: Boolean = false,
    val searching: Boolean = true,
    val reading: ProximityReading? = null,
    val error: String? = null
)

class FindPhoneActivity : ComponentActivity() {
    private lateinit var transport: DataLayerTransport
    private lateinit var messageClient: MessageClient

    private var scanner: BleProximityScanner? = null
    private var sessionToken: String? = null
    private var screenStarted = false
    private var stateReceiverRegistered = false

    private val callStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (hasAnyCallState()) {
                    stopSession()
                    finish()
                }
            }
        }

    private var state by
        mutableStateOf(
            FindPhoneUiState()
        )

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestMultiplePermissions()
        ) { result ->
            val granted =
                requiredPermissions()
                    .all {
                        result[it] == true ||
                            checkSelfPermission(
                                it
                            ) ==
                            PackageManager
                                .PERMISSION_GRANTED
                    }

            if (granted && screenStarted) {
                startSession()
            } else if (!granted) {
                state =
                    FindPhoneUiState(
                        searching = false,
                        error =
                            "Allow Nearby devices and precise location to use Bluetooth signal strength for Find Phone."
                    )
            }
        }

    private val messageListener =
        MessageClient
            .OnMessageReceivedListener {
                event ->
                val token =
                    sessionToken
                if (token != null) {
                    val payload =
                        event.data
                            .toString(
                                Charsets.UTF_8
                            )

                    when (event.path) {
                        Protocol.PROXIMITY_READY ->
                            if (payload == token) {
                                runOnUiThread {
                                    if (
                                        sessionToken ==
                                            token
                                    ) {
                                        state =
                                            state.copy(
                                                phoneReady =
                                                    true,
                                                error =
                                                    null
                                            )
                                    }
                                }
                            }

                        Protocol.PROXIMITY_ERROR ->
                            if (
                                payload.startsWith(
                                    "$token|"
                                )
                            ) {
                                val reason =
                                    payload
                                        .substringAfter(
                                            '|'
                                        )
                                        .ifBlank {
                                            "Phone could not start Bluetooth finding."
                                        }

                                runOnUiThread {
                                    failSession(
                                        reason
                                    )
                                }
                            }
                    }
                }
            }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        transport =
            DataLayerTransport(this)
        messageClient =
            Wearable.getMessageClient(
                this
            )

        setContent {
            MaterialTheme {
                FindPhoneScreen(
                    state = state,
                    onClose = ::finish,
                    onRetry = {
                        stopSession()
                        ensurePermissionAndStart()
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        screenStarted = true
        messageClient.addListener(
            messageListener
        )

        if (!stateReceiverRegistered) {
            val filter =
                IntentFilter(
                    Protocol.ACTION_STATE_CHANGED
                )

            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(
                    callStateReceiver,
                    filter,
                    RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(
                    callStateReceiver,
                    filter
                )
            }
            stateReceiverRegistered = true
        }

        ensurePermissionAndStart()
    }

    override fun onStop() {
        screenStarted = false
        stopSession()
        messageClient.removeListener(
            messageListener
        )

        if (stateReceiverRegistered) {
            unregisterReceiver(
                callStateReceiver
            )
            stateReceiverRegistered = false
        }

        super.onStop()
    }

    private fun ensurePermissionAndStart() {
        if (hasAnyCallState()) {
            state =
                FindPhoneUiState(
                    searching = false,
                    error =
                        "Finish the current call before using Find Phone."
                )
            return
        }

        val peerInfo =
            PeerInfoStore.get(
                this,
                EndpointRole.PHONE
            )
        if (
            peerInfo != null &&
            !peerInfo.capabilities.contains(
                Protocol
                    .CAPABILITY_BLE_PROXIMITY_V2
            )
        ) {
            state =
                FindPhoneUiState(
                    searching = false,
                    error =
                        "The Phone app needs the bidirectional BLE finding update."
                )
            return
        }

        val missing =
            requiredPermissions()
                .filter {
                    checkSelfPermission(it) !=
                        PackageManager
                            .PERMISSION_GRANTED
                }

        if (missing.isEmpty()) {
            startSession()
        } else {
            permissionLauncher.launch(
                missing.toTypedArray()
            )
        }
    }

    private fun hasAnyCallState(): Boolean =
        StateStore.incomingCall(this) != null ||
            StateStore.outgoingCall(this) != null ||
            StateStore.activeCall(this) != null

    private fun requiredPermissions():
        List<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            listOf(
                Manifest.permission
                    .BLUETOOTH_SCAN,
                Manifest.permission
                    .ACCESS_COARSE_LOCATION,
                Manifest.permission
                    .ACCESS_FINE_LOCATION
            )
        } else {
            listOf(
                Manifest.permission
                    .ACCESS_COARSE_LOCATION,
                Manifest.permission
                    .ACCESS_FINE_LOCATION
            )
        }

    private fun startSession() {
        if (
            sessionToken != null ||
            !screenStarted
        ) {
            return
        }

        val token =
            ProximitySessionToken.create()
        sessionToken = token
        state =
            FindPhoneUiState(
                searching = true
            )

        transport.sendSignal(
            Protocol.PROXIMITY_START,
            token
        ) { sent ->
            if (sessionToken != token) {
                return@sendSignal
            }

            if (!sent) {
                failSession(
                    "Phone is unreachable. Open HappyTalky on the Phone once, then try again."
                )
                return@sendSignal
            }

            val newScanner =
                BleProximityScanner(
                    context = this,
                    onReading = {
                        reading ->
                        if (
                            sessionToken ==
                                token
                        ) {
                            state =
                                state.copy(
                                    searching =
                                        reading ==
                                            null,
                                    reading =
                                        reading,
                                    error =
                                        null
                                )
                        }
                    },
                    onError = {
                        message ->
                        if (
                            sessionToken ==
                                token
                        ) {
                            failSession(
                                message
                            )
                        }
                    }
                )

            scanner = newScanner
            if (!newScanner.start(token)) {
                scanner = null
            }
        }
    }

    private fun failSession(
        message: String
    ) {
        val token =
            sessionToken
        scanner?.stop()
        scanner = null
        sessionToken = null

        if (token != null) {
            transport.sendSignal(
                Protocol.PROXIMITY_STOP,
                token
            ) { }
        }

        state =
            FindPhoneUiState(
                searching = false,
                error = message
            )
    }

    private fun stopSession() {
        val token =
            sessionToken
        scanner?.stop()
        scanner = null
        sessionToken = null

        if (token != null) {
            transport.sendSignal(
                Protocol.PROXIMITY_STOP,
                token
            ) { }
        }
    }
}

@Composable
internal fun FindPhoneScreen(
    state: FindPhoneUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    AppScaffold(
        containerColor = Color.Black,
        contentColor = Color.White,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier.width(154.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.spacedBy(
                        5.dp
                    ),
            ) {
                Text(
                    text = "FIND PHONE",
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,
                    color =
                        Color(0xFF8CC0FF),
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1,
                )

                if (state.error != null) {
                    Icon(
                        imageVector =
                            Icons.Rounded.Link,
                        contentDescription = null,
                        modifier =
                            Modifier.size(28.dp),
                        tint =
                            Color(0xFFFF8C9B),
                    )

                    Text(
                        text = state.error,
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,
                        color =
                            Color(0xFFD3D9E3),
                        textAlign =
                            TextAlign.Center,
                        maxLines = 4,
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                4.dp
                            )
                    ) {
                        TextButton(
                            onClick = onClose
                        ) {
                            Text("Close")
                        }
                        TextButton(
                            onClick = onRetry
                        ) {
                            Text("Retry")
                        }
                    }
                    return@Column
                }

                val reading =
                    state.reading

                Text(
                    text =
                        if (reading == null) {
                            "SEARCHING"
                        } else {
                            phoneBandLabel(
                                reading.band
                            )
                        },
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    color =
                        phoneBandColor(
                            reading?.band
                        ),
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1,
                )

                FindPhoneBars(
                    band = reading?.band
                )

                Text(
                    text =
                        if (reading == null) {
                            if (
                                state.phoneReady
                            ) {
                                "Listening for signal…"
                            } else {
                                "Starting phone beacon…"
                            }
                        } else {
                            phoneTrendLabel(
                                reading.trend
                            )
                        },
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    color =
                        Color(0xFFB7C2D2),
                    textAlign =
                        TextAlign.Center,
                    maxLines = 1,
                )

                if (reading != null) {
                    Text(
                        text =
                            reading.filteredRssi
                                .roundToInt()
                                .toString() +
                                " dBm",
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,
                        color =
                            Color(0xFF7F8DA2),
                        maxLines = 1,
                    )
                } else {
                    Spacer(
                        Modifier.height(2.dp)
                    )
                }

                TextButton(
                    onClick = onClose,
                    modifier =
                        Modifier
                            .width(86.dp)
                            .height(30.dp),
                ) {
                    Text(
                        text = "Stop",
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun FindPhoneBars(
    band: ProximityBand?
) {
    val active =
        when (band) {
            ProximityBand.FAR -> 1
            ProximityBand.NEARBY -> 2
            ProximityBand.CLOSE -> 3
            ProximityBand.VERY_CLOSE -> 4
            null -> 0
        }

    Row(
        horizontalArrangement =
            Arrangement.spacedBy(
                4.dp
            ),
        verticalAlignment =
            Alignment.Bottom,
    ) {
        repeat(4) {
            index ->
            Box(
                modifier =
                    Modifier
                        .width(17.dp)
                        .height(
                            (7 + index * 4)
                                .dp
                        )
                        .background(
                            color =
                                if (
                                    index < active
                                ) {
                                    phoneBandColor(
                                        band
                                    )
                                } else {
                                    Color(
                                        0xFF263347
                                    )
                                },
                            shape =
                                RoundedCornerShape(
                                    3.dp
                                )
                        )
            )
        }
    }
}

private fun phoneBandLabel(
    band: ProximityBand
): String =
    when (band) {
        ProximityBand.FAR -> "FAR"
        ProximityBand.NEARBY -> "NEARBY"
        ProximityBand.CLOSE -> "CLOSE"
        ProximityBand.VERY_CLOSE ->
            "VERY CLOSE"
    }

private fun phoneBandColor(
    band: ProximityBand?
): Color =
    when (band) {
        ProximityBand.FAR ->
            Color(0xFFFFA45E)

        ProximityBand.NEARBY ->
            Color(0xFFFFD35A)

        ProximityBand.CLOSE ->
            Color(0xFF71DFA4)

        ProximityBand.VERY_CLOSE ->
            Color(0xFF2DDE91)

        null ->
            Color(0xFF8CC0FF)
    }

private fun phoneTrendLabel(
    trend: ProximityTrend
): String =
    when (trend) {
        ProximityTrend.GETTING_CLOSER ->
            "Getting closer ↑"

        ProximityTrend.GETTING_FARTHER ->
            "Getting farther ↓"

        ProximityTrend.STEADY ->
            "Signal steady"
    }
