package com.xiaolong.happytalky.core

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

enum class CallVisualState {
    READY,
    INCOMING,
    OUTGOING,
    CONNECTING,
    RECONNECTING,
    LIVE
}

data class HappyTalkyUiState(
    val status: String = "Checking connection…",
    val callState: CallVisualState = CallVisualState.READY,
    val recording: Boolean = false,
    val callEnabled: Boolean = false,
    val talkEnabled: Boolean = true,
    val speakerOn: Boolean = false,
    val peerName: String = "Watch",
    val peerCapabilities: Set<String> = emptySet(),
    val priorityCallAvailable: Boolean = false,
    val priorityLocked: Boolean = false,
    val textEnabled: Boolean = false,
    val unreadTextCount: Int = 0,
    val callMode: CallMode = CallMode.NORMAL,
    val peerConnection: PeerConnectionState = PeerConnectionState.UNKNOWN,
    val peerRoute: PeerRoute = PeerRoute.UNKNOWN,
    val messages: List<VoiceMessage> = emptyList(),
    val unreadVoiceCount: Int = 0,
    val callHistory: List<CallHistoryEntry> = emptyList(),
    val timeline: List<ConversationItem> = emptyList()
)

abstract class HappyTalkyActivity : ComponentActivity() {
    private lateinit var role: EndpointRole
    private lateinit var transport: DataLayerTransport
    private lateinit var recorder: AudioRecorder
    private lateinit var connectivityManager: ConnectivityManager

    protected var uiState by mutableStateOf(HappyTalkyUiState())
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var recording = false
    private var receiverRegistered = false
    private var networkCallbackRegistered = false
    private var activityResumed = false
    private var recordingTimeout: Runnable? = null
    private var callTimeout: Runnable? = null

    private val stateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                refreshUiState()
                maybeAutoAnswerPriorityCall()
            }
        }

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refreshPeerRoute()
            }

            override fun onLost(network: Network) {
                refreshPeerRoute()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                refreshPeerRoute()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        role = EndpointRole.fromContext(this)
        transport = DataLayerTransport(this)
        transport.publishDeviceInfo()
        recorder = AudioRecorder(this)
        connectivityManager =
            getSystemService(ConnectivityManager::class.java)

        requestNeededPermissions()
        updateIncomingPresentation()
        refreshUiState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateIncomingPresentation()
        refreshUiState()
        maybeAutoAnswerPriorityCall()
    }

    override fun onStart() {
        super.onStart()

        if (!receiverRegistered) {
            val filter =
                IntentFilter(
                    Protocol.ACTION_STATE_CHANGED
                )

            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(
                    stateReceiver,
                    filter,
                    RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(
                    stateReceiver,
                    filter
                )
            }

            receiverRegistered = true
        }

        if (!networkCallbackRegistered) {
            runCatching {
                connectivityManager.registerDefaultNetworkCallback(
                    networkCallback
                )
                networkCallbackRegistered = true
            }
        }

        refreshPeerRoute()
        refreshUiState()
        maybeAutoAnswerPriorityCall()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        maybeAutoAnswerPriorityCall()
    }

    override fun onPause() {
        activityResumed = false
        super.onPause()
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(stateReceiver)
            receiverRegistered = false
        }

        if (networkCallbackRegistered) {
            runCatching {
                connectivityManager.unregisterNetworkCallback(
                    networkCallback
                )
            }
            networkCallbackRegistered = false
        }

        super.onStop()
    }

    override fun onDestroy() {
        recordingTimeout?.let(handler::removeCallbacks)
        callTimeout?.let(handler::removeCallbacks)
        recordingTimeout = null
        callTimeout = null
        if (recording) {
            recorder.cancel()
        }

        super.onDestroy()
    }

    protected fun handleCallAction() {
        val incoming = StateStore.incomingCall(this)
        val outgoing = StateStore.outgoingCall(this)
        val active = StateStore.activeCall(this)

        when {
            incoming != null ->
                answerCall(incoming)

            active != null &&
                PriorityCallPolicy.canLocalTerminate(
                    localRole = role,
                    locked =
                        StateStore.priorityLocked(
                            this
                        )
                ) ->
                endCall(active)

            outgoing != null ->
                cancelOutgoingCall(outgoing)

            active == null ->
                startCall()
        }
    }

    protected fun declineIncomingCall() {
        val callId =
            StateStore.incomingCall(this)
                ?: return

        if (
            !PriorityCallPolicy.canLocalTerminate(
                localRole = role,
                locked =
                    StateStore.priorityLocked(
                        this
                    )
            )
        ) {
            return
        }

        CallHistoryStore.append(
            this,
            callId,
            CallDirection.INCOMING,
            CallOutcome.DECLINED_BY_ME
        )
        StateStore.clearCallState(this)
        StateStore.setStatus(this, "Call declined")
        AlertController.stop(this)
        EventBus.notifyStateChanged(this)
        refreshUiState()

        transport.sendSignal(
            Protocol.CALL_DECLINE,
            callId
        ) { }
    }

    protected fun requestPriorityCall() {
        if (
            role != EndpointRole.PHONE ||
            !priorityCallIsAvailable()
        ) {
            return
        }

        val callId =
            UUID.randomUUID().toString()
        val peer =
            peerName()

        StateStore.setCallInitiator(
            this,
            true
        )
        StateStore.setCallMode(
            this,
            CallMode.PRIORITY
        )
        StateStore.setPriorityLocked(
            this,
            true
        )
        StateStore.setOutgoingStartedAt(
            this,
            System.currentTimeMillis()
        )
        StateStore.setOutgoingCall(
            this,
            callId
        )
        StateStore.setStatus(
            this,
            "Starting priority call to $peer…"
        )
        LiveCallService.start(this)
        refreshUiState()

        transport.sendSignal(
            Protocol.CALL_PRIORITY_LOCKED,
            callId
        ) { sent ->
            if (
                StateStore.outgoingCall(this) !=
                    callId
            ) {
                return@sendSignal
            }

            if (!sent) {
                CallHistoryStore.append(
                    this,
                    callId,
                    CallDirection.OUTGOING,
                    CallOutcome.FAILED
                )
                StateStore.clearCallState(
                    this
                )
                StateStore.setStatus(
                    this,
                    "$peer is unreachable"
                )
                LiveCallService.stop(this)
            } else {
                StateStore.setStatus(
                    this,
                    "Priority call sent · waiting for Watch"
                )
            }

            refreshUiState()
        }
    }


    protected fun sendText(
        rawText: String
    ) {
        if (lockedPriorityInteractionBlocked()) {
            return
        }

        val peerInfo =
            peerInfo()
        val connection =
            StateStore.peerConnection(this)

        if (
            !TextCapabilityPolicy.canSend(
                connection = connection,
                peerInfo = peerInfo
            )
        ) {
            StateStore.setStatus(
                this,
                "Peer app does not support text yet"
            )
            refreshUiState()
            return
        }

        val text =
            TextMessageStore.normalize(
                rawText
            )
                ?: return
        val saved =
            TextMessageStore.saveOutgoing(
                this,
                text
            )

        val peerConnected =
            StateStore.peerConnection(this) ==
                PeerConnectionState.CONNECTED

        StateStore.setStatus(
            this,
            if (peerConnected) {
                "Sending text…"
            } else {
                "Saving text for delivery…"
            }
        )
        refreshUiState()

        transport.queueText(
            saved,
            role
        ) { accepted ->
            val state =
                when {
                    !accepted ->
                        DeliveryState.LOCAL

                    peerConnected ->
                        DeliveryState.SENT

                    else ->
                        DeliveryState.QUEUED
                }

            ConversationStore.updateDeliveryState(
                this,
                saved.id,
                state
            )
            StateStore.setStatus(
                this,
                when (state) {
                    DeliveryState.SENT ->
                        "Text sent"

                    DeliveryState.QUEUED ->
                        "Text queued · will sync when connected"

                    else ->
                        "Text saved locally · retry later"
                }
            )
            refreshUiState()
        }
    }

    protected fun markTextMessagesRead() {
        val changed =
            TextMessageStore.markAllRead(
                this
            )

        if (changed > 0) {
            AlertController
                .refreshMessageNotification(
                    this
                )
            refreshUiState()
        }
    }

    protected fun beginTalk() {
        if (recording || hasAnyCallState()) return

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),
                REQUEST_PERMISSIONS
            )
            StateStore.setStatus(
                this,
                "Microphone permission required"
            )
            refreshUiState()
            return
        }

        if (recorder.start()) {
            recording = true
            StateStore.setStatus(this, "Recording TALK…")
            refreshUiState()

            recordingTimeout?.let(handler::removeCallbacks)
            recordingTimeout =
                Runnable {
                    recordingTimeout = null
                    if (recording) finishTalk()
                }.also {
                    handler.postDelayed(
                        it,
                        Protocol.MAX_RECORDING_MS.toLong()
                    )
                }
        } else {
            StateStore.setStatus(
                this,
                "Could not start microphone"
            )
            refreshUiState()
        }
    }

    protected fun finishTalk() {
        if (!recording) return

        recording = false
        recordingTimeout?.let(handler::removeCallbacks)
        recordingTimeout = null

        val temp = recorder.stop()
        if (temp == null) {
            StateStore.setStatus(
                this,
                "Recording was too short"
            )
            refreshUiState()
            return
        }

        val saved =
            try {
                VoiceMessageStore.saveOutgoing(
                    this,
                    temp
                )
            } catch (_: Exception) {
                temp.delete()
                StateStore.setStatus(
                    this,
                    "Could not save voice message"
                )
                refreshUiState()
                return
            }

        StateStore.setStatus(
            this,
            if (
                StateStore.peerConnection(this) ==
                    PeerConnectionState.CONNECTED
            ) {
                "Sending TALK…"
            } else {
                "Saving TALK for delivery…"
            }
        )
        refreshUiState()

        transport.queueVoice(
            saved,
            role
        ) { queued ->
            ConversationStore.updateDeliveryState(
                this,
                saved.id,
                if (queued) {
                    DeliveryState.QUEUED
                } else {
                    DeliveryState.LOCAL
                }
            )
            StateStore.setStatus(
                this,
                if (queued) {
                    if (
                        StateStore.peerConnection(this) ==
                            PeerConnectionState.CONNECTED
                    ) {
                        "TALK queued for delivery"
                    } else {
                        "TALK saved · will send when connected"
                    }
                } else {
                    "Saved locally · retry later"
                }
            )
            refreshUiState()
        }
    }

    protected fun cancelTalk() {
        if (!recording) return

        recording = false
        recordingTimeout?.let(handler::removeCallbacks)
        recordingTimeout = null
        recorder.cancel()
        StateStore.setStatus(this, "Recording cancelled")
        refreshUiState()
    }

    protected fun playMessage(
        message: VoiceMessage
    ) {
        if (hasAnyCallState()) {
            return
        }

        AudioPlayer.play(
            this,
            message.file,
            deleteAfter = false
        ) {
            if (
                message.direction ==
                    VoiceDirection.INCOMING &&
                !message.isRead
            ) {
                VoiceMessageStore.markRead(
                    this,
                    message.id
                )
                AlertController.refreshVoiceNotification(
                    this
                )
                refreshUiState()
            }
        }
    }

    protected fun deleteMessages(
        ids: Set<String>
    ) {
        AudioPlayer.stop()
        val deleted =
            VoiceMessageStore.delete(
                this,
                ids
            )

        if (deleted > 0) {
            AlertController.refreshVoiceNotification(this)
            StateStore.setStatus(
                this,
                "$deleted TALK message" +
                    if (deleted == 1) {
                        " deleted"
                    } else {
                        "s deleted"
                    }
            )
        }

        refreshUiState()
    }

    protected fun deleteConversationItems(
        ids: Set<String>
    ) {
        if (ids.isEmpty()) {
            return
        }

        AudioPlayer.stop()
        val voiceDeleted =
            VoiceMessageStore.delete(
                this,
                ids
            )
        val metadataDeleted =
            ConversationStore.deleteItems(
                this,
                ids
            )
        val deleted =
            voiceDeleted +
                metadataDeleted

        if (deleted > 0) {
            AlertController
                .refreshMessageNotification(
                    this
                )
            StateStore.setStatus(
                this,
                if (deleted == 1) {
                    "Conversation item deleted"
                } else {
                    "$deleted conversation items deleted"
                }
            )
        }

        refreshUiState()
    }

    protected fun clearMessages() {
        AudioPlayer.stop()
        val deleted =
            VoiceMessageStore.clear(this)

        AlertController.refreshVoiceNotification(this)
        StateStore.setStatus(
            this,
            if (deleted > 0) {
                "TALK history cleared"
            } else {
                "No TALK history to clear"
            }
        )
        refreshUiState()
    }

    protected fun toggleSpeaker() {
        if (
            StateStore.activeCall(this) == null ||
            !LiveCallAudio.isRunning()
        ) {
            return
        }

        val next =
            !LiveCallAudio.isSpeakerEnabled(this)

        LiveCallAudio.setSpeakerEnabled(
            this,
            next
        )
        refreshUiState()
    }

    protected fun refreshNow() {
        refreshPeerRoute()
        refreshUiState()
    }

    private fun refreshPeerRoute() {
        if (!::transport.isInitialized) return

        handler.post {
            transport.refreshPeerConnection {
                    _,
                    _ ->
                refreshUiState()
            }
        }
    }

    private fun startCall() {
        val connection = StateStore.peerConnection(this)
        val route = StateStore.peerRoute(this)

        if (!CallRoutePolicy.canStartCall(connection, route)) {
            StateStore.setStatus(
                this,
                when (route) {
                    PeerRoute.RECONNECTING ->
                        "Reconnecting · try CALL when ready"
                    PeerRoute.UNKNOWN ->
                        "Checking connection · try CALL when ready"
                    PeerRoute.OFFLINE ->
                        "${peerName()} is offline · TALK recommended"
                    else ->
                        "${peerName()} is unavailable · TALK recommended"
                }
            )
            refreshPeerRoute()
            refreshUiState()
            return
        }

        val callId = UUID.randomUUID().toString()
        val peer = peerName()

        StateStore.setCallInitiator(this, true)
        StateStore.setCallMode(
            this,
            CallMode.NORMAL
        )
        StateStore.setOutgoingStartedAt(
            this,
            System.currentTimeMillis()
        )
        StateStore.setOutgoingCall(this, callId)
        StateStore.setStatus(this, "Calling $peer…")
        LiveCallService.start(this)
        refreshUiState()

        transport.sendSignal(
            Protocol.CALL_RING,
            callId
        ) { sent ->
            if (
                StateStore.outgoingCall(this) !=
                    callId
            ) {
                return@sendSignal
            }

            if (!sent) {
                CallHistoryStore.append(
                    this,
                    callId,
                    CallDirection.OUTGOING,
                    CallOutcome.FAILED
                )
                StateStore.clearCallState(this)
                StateStore.setStatus(
                    this,
                    "$peer is offline · TALK recommended"
                )
                LiveCallService.stop(this)
                refreshUiState()
                return@sendSignal
            }

            StateStore.setStatus(
                this,
                "Ringing $peer…"
            )
            refreshUiState()

            callTimeout?.let(handler::removeCallbacks)
            callTimeout =
                Runnable {
                    if (
                        StateStore.outgoingCall(this) ==
                            callId &&
                        StateStore.activeCall(this) ==
                            null
                    ) {
                        CallHistoryStore.append(
                            this,
                            callId,
                            CallDirection.OUTGOING,
                            CallOutcome.NO_ANSWER
                        )
                        StateStore.clearCallState(this)
                        StateStore.setStatus(
                            this,
                            "No answer · TALK recommended"
                        )
                        LiveCallService.stop(this)
                        refreshUiState()

                        transport.sendSignal(
                            Protocol.CALL_CANCEL,
                            callId
                        ) { }
                    }

                    callTimeout = null
                }.also {
                    handler.postDelayed(
                        it,
                        Protocol.CALL_TIMEOUT_MS
                    )
                }
        }
    }

    private fun cancelOutgoingCall(
        callId: String
    ) {
        callTimeout?.let(handler::removeCallbacks)
        callTimeout = null

        CallHistoryStore.append(
            this,
            callId,
            CallDirection.OUTGOING,
            CallOutcome.CANCELLED_BY_ME
        )
        StateStore.clearCallState(this)
        StateStore.setStatus(this, "Call cancelled")
        LiveCallService.stop(this)
        refreshUiState()

        transport.sendSignal(
            Protocol.CALL_CANCEL,
            callId
        ) { }
    }

    private fun answerCall(callId: String) {
        AlertController.stop(this)

        StateStore.setIncomingCall(this, null)
        StateStore.setCallInitiator(this, false)
        StateStore.setActiveCall(this, callId)
        StateStore.setActiveStartedAt(
            this,
            System.currentTimeMillis()
        )
        StateStore.clearReconnectWindow(this)
        StateStore.setStatus(
            this,
            "Connecting live audio…"
        )

        LiveCallService.start(this)
        EventBus.notifyStateChanged(this)
        refreshUiState()

        transport.sendSignal(
            Protocol.CALL_ANSWER,
            callId
        ) { sent ->
            if (
                !sent &&
                StateStore.activeCall(this) ==
                    callId
            ) {
                CallHistoryStore.append(
                    this,
                    callId,
                    CallDirection.INCOMING,
                    CallOutcome.FAILED
                )
                StateStore.clearCallState(this)
                StateStore.setStatus(
                    this,
                    peerName() + " is unreachable"
                )
                LiveCallService.stop(this)
                refreshUiState()
            }
        }
    }

    private fun endCall(callId: String) {
        if (
            !PriorityCallPolicy.canLocalTerminate(
                localRole = role,
                locked =
                    StateStore.priorityLocked(
                        this
                    )
            )
        ) {
            return
        }

        callTimeout?.let(handler::removeCallbacks)
        callTimeout = null

        val direction =
            if (StateStore.callInitiator(this)) {
                CallDirection.OUTGOING
            } else {
                CallDirection.INCOMING
            }
        val startedAt =
            StateStore.activeStartedAt(this)

        CallHistoryStore.append(
            this,
            callId,
            direction,
            CallOutcome.COMPLETED,
            startedAt = startedAt
        )

        StateStore.clearCallState(this)
        StateStore.setStatus(this, "Call ended")
        AlertController.stop(this)
        LiveCallAudio.stop(this)
        CallHaptics.ended(
            this,
            callId
        )
        LiveCallService.stop(this)
        refreshUiState()

        transport.sendSignal(
            Protocol.CALL_END,
            callId
        ) { }
    }

    private fun refreshUiState() {
        val incoming = StateStore.incomingCall(this)
        updateIncomingPresentation(incoming != null)
        val outgoing = StateStore.outgoingCall(this)
        val active = StateStore.activeCall(this)
        val connection = StateStore.peerConnection(this)
        val route = StateStore.peerRoute(this)
        val live = LiveCallAudio.isRunning()
        val peer = peerName()
        val peerInfo = peerInfo()
        val callMode =
            StateStore.callMode(this)
        val priorityLocked =
            StateStore.priorityLocked(
                this
            )

        val visualState =
            when {
                active != null && live ->
                    CallVisualState.LIVE

                active != null &&
                    (
                        connection ==
                            PeerConnectionState.RECONNECTING ||
                            route ==
                                PeerRoute.RECONNECTING
                    ) ->
                    CallVisualState.RECONNECTING

                active != null ->
                    CallVisualState.CONNECTING

                incoming != null ->
                    CallVisualState.INCOMING

                outgoing != null ->
                    CallVisualState.OUTGOING

                else ->
                    CallVisualState.READY
            }

        val status =
            when (visualState) {
                CallVisualState.LIVE ->
                    "Live with $peer"

                CallVisualState.RECONNECTING ->
                    "Reconnecting…"

                CallVisualState.CONNECTING ->
                    "Connecting live audio…"

                CallVisualState.INCOMING ->
                    "$peer is calling"

                CallVisualState.OUTGOING ->
                    "Calling $peer…"

                CallVisualState.READY ->
                    StateStore.status(this)
            }

        val callInProgress =
            incoming != null ||
                outgoing != null ||
                active != null

        uiState =
            HappyTalkyUiState(
                status = status,
                callState = visualState,
                recording = recording,
                callEnabled =
                    if (
                        role == EndpointRole.WATCH &&
                        priorityLocked &&
                        callInProgress
                    ) {
                        false
                    } else {
                        callInProgress ||
                            (
                                !recording &&
                                    CallRoutePolicy
                                        .canStartCall(
                                            connection,
                                            route
                                        )
                                )
                    },
                talkEnabled =
                    !callInProgress,
                speakerOn =
                    active != null &&
                        live &&
                        LiveCallAudio.isSpeakerEnabled(this),
                peerName = peer,
                peerCapabilities =
                    peerInfo?.capabilities
                        .orEmpty(),
                priorityCallAvailable =
                    priorityCallIsAvailable(),
                priorityLocked =
                    priorityLocked,
                textEnabled =
                    TextCapabilityPolicy.canSend(
                        connection = connection,
                        peerInfo = peerInfo
                    ),
                unreadTextCount =
                    TextMessageStore.unreadCount(
                        this
                    ),
                callMode = callMode,
                peerConnection = connection,
                peerRoute = route,
                messages =
                    VoiceMessageStore.list(
                        this,
                        limit = 60
                    ),
                unreadVoiceCount =
                    VoiceMessageStore.unreadCount(
                        this
                    ),
                callHistory =
                    CallHistoryStore.list(
                        this,
                        limit = 60
                    ),
                timeline =
                    ConversationStore.timeline(
                        this,
                        limit = 60
                    )
            )
    }

    private fun priorityCallIsAvailable(): Boolean {
        val incoming =
            StateStore.incomingCall(this)
        val outgoing =
            StateStore.outgoingCall(this)
        val active =
            StateStore.activeCall(this)
        val peer =
            peerInfo()

        return PriorityCallPolicy
            .canRequestLocked(
                localRole = role,
                callInProgress =
                    incoming != null ||
                        outgoing != null ||
                        active != null,
                recording = recording,
                peerConnected =
                    StateStore.peerConnection(
                        this
                    ) ==
                        PeerConnectionState
                            .CONNECTED &&
                        CallRoutePolicy
                            .canStartCall(
                                StateStore
                                    .peerConnection(
                                        this
                                    ),
                                StateStore
                                    .peerRoute(this)
                            ),
                peerSupportsLockedPriority =
                    peer?.capabilities
                        ?.contains(
                            Protocol
                                .CAPABILITY_PRIORITY_LOCKED_CALL_V1
                        ) == true
            )
    }

    private fun maybeAutoAnswerPriorityCall() {
        val callId =
            StateStore.incomingCall(this)

        if (
            !PriorityCallPolicy.canAutoAnswer(
                localRole = role,
                locked =
                    StateStore.priorityLocked(
                        this
                    ),
                legacyEnabled =
                    PriorityCallSettings.isEnabled(
                        this
                    ),
                mode =
                    StateStore.callMode(
                        this
                    ),
                incomingCallPresent =
                    callId != null,
                activityVisible =
                    activityResumed
            )
        ) {
            return
        }

        callId ?: return

        // Core-Telecom handles locked Priority calls in the background.
        // Keep the visible-Activity path as a fallback for devices where
        // Telecom setup is unavailable or fails.
        handler.post {
            if (
                StateStore.incomingCall(this) ==
                    callId &&
                StateStore.callMode(this) ==
                    CallMode.PRIORITY
            ) {
                answerCall(callId)
            }
        }
    }

    private fun hasAnyCallState(): Boolean =
        StateStore.incomingCall(this) != null ||
            StateStore.outgoingCall(this) != null ||
            StateStore.activeCall(this) != null

    private fun lockedPriorityInteractionBlocked(): Boolean =
        role == EndpointRole.WATCH &&
            StateStore.priorityLocked(this) &&
            hasAnyCallState()

    private fun updateIncomingPresentation(
        incoming: Boolean =
            StateStore.incomingCall(this) != null
    ) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(incoming)
            setTurnScreenOn(incoming)
        } else {
            @Suppress("DEPRECATION")
            if (incoming) {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            } else {
                window.clearFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }
    }

    private fun requestNeededPermissions() {
        val missing =
            mutableListOf<String>()

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missing +=
                Manifest.permission.RECORD_AUDIO
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missing +=
                Manifest.permission.POST_NOTIFICATIONS
        }

        if (
            Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missing +=
                Manifest.permission.BLUETOOTH_ADVERTISE
        }

        if (missing.isNotEmpty()) {
            requestPermissions(
                missing.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        }
    }

    protected fun peerSupports(
        capability: String
    ): Boolean =
        peerInfo()
            ?.capabilities
            ?.contains(capability) == true

    private fun peerInfo(): DeviceInfo? =
        PeerInfoStore.get(
            this,
            if (role == EndpointRole.PHONE) {
                EndpointRole.WATCH
            } else {
                EndpointRole.PHONE
            }
        )

    private fun peerName(): String =
        PeerInfoStore.peerLabel(
            this,
            role
        )

    companion object {
        private const val REQUEST_PERMISSIONS = 42
    }
}
