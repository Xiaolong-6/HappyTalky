package com.xldev.happytalky.mobile

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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

data class FindWatchUiState(
    val watchReady: Boolean = false,
    val searching: Boolean = true,
    val reading: ProximityReading? = null,
    val error: String? = null
)

class FindWatchActivity : ComponentActivity() {
    private lateinit var transport:
        DataLayerTransport
    private lateinit var messageClient:
        MessageClient

    private var scanner:
        BleProximityScanner? = null
    private var sessionToken:
        String? = null
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
            FindWatchUiState()
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
                    FindWatchUiState(
                        searching = false,
                        error =
                            if (
                                Build.VERSION.SDK_INT >=
                                    31
                            ) {
                                "Allow Nearby devices and precise location so Bluetooth signal strength can guide nearby finding."
                            } else {
                                "Allow location while using the app for Bluetooth scanning on this Android version."
                            }
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
                                                watchReady =
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
                                            "Watch could not start Bluetooth finding."
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
        enableEdgeToEdge()

        transport =
            DataLayerTransport(this)
        messageClient =
            Wearable.getMessageClient(
                this
            )

        setContent {
            HappyTalkyPhoneTheme {
                FindWatchScreen(
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
        val peerInfo =
            PeerInfoStore.get(
                this,
                EndpointRole.WATCH
            )
        if (
            peerInfo != null &&
            !peerInfo.capabilities.contains(
                Protocol
                    .CAPABILITY_BLE_PROXIMITY_V1
            )
        ) {
            state =
                FindWatchUiState(
                    searching = false,
                    error =
                        "The Watch app needs the BLE finding update."
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
            FindWatchUiState(
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
                    "Watch is unreachable. Open HappyTalky on the Watch once, then try again."
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
            FindWatchUiState(
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
fun FindWatchScreen(
    state: FindWatchUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        containerColor =
            MaterialTheme
                .colorScheme
                .background,
        contentWindowInsets =
            WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color =
                    MaterialTheme
                        .colorScheme
                        .background,
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(60.dp)
                            .padding(
                                horizontal =
                                    8.dp
                            ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onClose
                    ) {
                        Icon(
                            imageVector =
                                Icons.Rounded.Close,
                            contentDescription =
                                "Close Find Watch"
                        )
                    }
                    Text(
                        text = "Find Watch",
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,
                        fontWeight =
                            FontWeight.SemiBold,
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(
                        horizontal = 28.dp,
                        vertical = 24.dp
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center,
        ) {
            ProximitySignalVisual(
                score =
                    state.reading
                        ?.signalScore
                        ?: 0,
                active =
                    state.error ==
                        null
            )

            Spacer(
                Modifier.height(30.dp)
            )

            val status =
                when {
                    state.error != null ->
                        "Nearby search unavailable"

                    state.reading != null ->
                        bandLabel(
                            state.reading.band
                        )

                    state.watchReady ->
                        "Searching nearby"

                    else ->
                        "Starting Watch beacon"
                }

            Text(
                text = status,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight =
                    FontWeight.Bold,
                textAlign =
                    TextAlign.Center,
            )

            Spacer(
                Modifier.height(10.dp)
            )

            Text(
                text =
                    when {
                        state.error != null ->
                            state.error

                        state.reading != null ->
                            trendLabel(
                                state.reading
                                    .trend
                            )

                        state.watchReady ->
                            "Move around slowly until the signal gets stronger."

                        else ->
                            "Asking the Watch to start a temporary Bluetooth beacon…"
                    },
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                textAlign =
                    TextAlign.Center,
            )

            Spacer(
                Modifier.height(28.dp)
            )

            LinearProgressIndicator(
                progress = {
                    (
                        state.reading
                            ?.signalScore
                            ?: 0
                        ) / 100f
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp),
            )

            Spacer(
                Modifier.height(14.dp)
            )

            Text(
                text =
                    "Bluetooth signal strength only · no exact distance or direction",
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                textAlign =
                    TextAlign.Center,
            )

            if (state.error != null) {
                Spacer(
                    Modifier.height(24.dp)
                )
                Button(
                    onClick = onRetry,
                    contentPadding =
                        PaddingValues(
                            horizontal =
                                22.dp,
                            vertical =
                                12.dp
                        )
                ) {
                    Icon(
                        imageVector =
                            Icons.Rounded.Refresh,
                        contentDescription =
                            null
                    )
                    Spacer(
                        Modifier.size(8.dp)
                    )
                    Text("Try again")
                }
            }
        }
    }
}

@Composable
private fun ProximitySignalVisual(
    score: Int,
    active: Boolean
) {
    val fraction =
        score
            .coerceIn(0, 100) /
            100f
    val primary =
        MaterialTheme
            .colorScheme
            .primary

    Box(
        modifier =
            Modifier.size(220.dp),
        contentAlignment =
            Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(220.dp)
                    .alpha(
                        if (active) {
                            0.08f +
                                0.12f *
                                fraction
                        } else {
                            0.05f
                        }
                    )
                    .background(
                        primary,
                        CircleShape
                    )
        )
        Box(
            modifier =
                Modifier
                    .size(158.dp)
                    .alpha(
                        if (active) {
                            0.12f +
                                0.18f *
                                fraction
                        } else {
                            0.07f
                        }
                    )
                    .background(
                        primary,
                        CircleShape
                    )
        )
        Surface(
            modifier =
                Modifier.size(96.dp),
            shape = CircleShape,
            color =
                MaterialTheme
                    .colorScheme
                    .primaryContainer,
        ) {
            Box(
                contentAlignment =
                    Alignment.Center
            ) {
                if (active) {
                    Icon(
                        imageVector =
                            Icons.Rounded.Watch,
                        contentDescription =
                            null,
                        modifier =
                            Modifier.size(44.dp),
                        tint =
                            MaterialTheme
                                .colorScheme
                                .onPrimaryContainer,
                    )
                } else {
                    Text(
                        text = "!",
                        fontSize = 24.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onPrimaryContainer,
                    )
                }
            }
        }
    }
}

private fun bandLabel(
    band: ProximityBand
): String =
    when (band) {
        ProximityBand.FAR ->
            "Far"

        ProximityBand.NEARBY ->
            "Nearby"

        ProximityBand.CLOSE ->
            "Close"

        ProximityBand.VERY_CLOSE ->
            "Very close"
    }

private fun trendLabel(
    trend: ProximityTrend
): String =
    when (trend) {
        ProximityTrend.GETTING_CLOSER ->
            "Getting closer ↑"

        ProximityTrend.GETTING_FARTHER ->
            "Getting farther ↓"

        ProximityTrend.STEADY ->
            "About the same — move around slowly"
    }
