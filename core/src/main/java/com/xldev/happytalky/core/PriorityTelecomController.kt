package com.xldev.happytalky.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.DisconnectCause
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the platform Telecom session for a locked Priority call received on
 * Wear OS. The Data Layer remains the signalling/media transport; Telecom is
 * used to give the call a real system call lifecycle so Android can grant
 * background communication execution and microphone access without waiting
 * for the Watch Activity to be opened.
 */
object PriorityTelecomController {
    private const val TAG = "HappyTalkyPriority"
    private const val SPEAKER_ROUTE_TIMEOUT_MS = 2_000L

    private val worker =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Default
        )

    private data class Session(
        val callId: String,
        val control: CallControlScope
    )

    @Volatile
    private var pendingCallId: String? = null

    @Volatile
    private var session: Session? = null

    private val cancelledBeforeAttach =
        mutableSetOf<String>()

    private val requestedClose =
        mutableSetOf<String>()

    private val fallbackToActivity =
        mutableSetOf<String>()

    fun isSupported(context: Context): Boolean =
        context.packageManager
            .hasSystemFeature(
                PackageManager.FEATURE_TELECOM
            ) &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

    fun isManaging(callId: String?): Boolean {
        if (callId == null) return false

        return synchronized(this) {
            pendingCallId == callId ||
                session?.callId == callId
        }
    }

    fun startLockedIncoming(
        context: Context,
        callId: String
    ) {
        val appContext =
            context.applicationContext

        if (!isSupported(appContext)) {
            StateStore.setStatus(
                appContext,
                "Priority call · open watch to connect"
            )
            EventBus.notifyStateChanged(appContext)
            return
        }

        synchronized(this) {
            if (
                pendingCallId == callId ||
                session?.callId == callId
            ) {
                return
            }

            if (
                pendingCallId != null ||
                session != null
            ) {
                Log.w(
                    TAG,
                    "Ignoring overlapping Telecom request"
                )
                return
            }

            pendingCallId = callId
            cancelledBeforeAttach.remove(callId)
        }

        worker.launch {
            runTelecomSession(
                appContext,
                callId
            )
        }
    }

    fun disconnectFromPhone(
        context: Context,
        callId: String,
        disconnected: Boolean = false
    ) {
        val appContext =
            context.applicationContext
        val attached =
            synchronized(this) {
                requestedClose.add(callId)
                if (pendingCallId == callId) {
                    cancelledBeforeAttach.add(callId)
                }
                session?.takeIf {
                    it.callId == callId
                }
            }

        if (attached == null) return

        worker.launch {
            runCatching {
                attached.control.disconnect(
                    DisconnectCause(
                        if (disconnected) {
                            DisconnectCause.ERROR
                        } else {
                            DisconnectCause.REMOTE
                        }
                    )
                )
            }.onFailure {
                Log.w(
                    TAG,
                    "Failed to close Telecom session",
                    it
                )
            }
        }
    }

    fun disconnectForFailure(
        context: Context,
        callId: String
    ) {
        val attached =
            synchronized(this) {
                requestedClose.add(callId)
                session?.takeIf {
                    it.callId == callId
                }
            } ?: return

        worker.launch {
            runCatching {
                attached.control.disconnect(
                    DisconnectCause(
                        DisconnectCause.ERROR
                    )
                )
            }
        }
    }

    private suspend fun runTelecomSession(
        context: Context,
        callId: String
    ) {
        try {
            val callsManager =
                CallsManager(context)

            callsManager
                .registerAppWithTelecom(
                    CallsManager
                        .CAPABILITY_BASELINE
                )

            val attributes =
                CallAttributesCompat(
                    displayName =
                        "HappyTalky Priority",
                    address =
                        Uri.parse(
                            "happytalky:$callId"
                        ),
                    direction =
                        CallAttributesCompat
                            .DIRECTION_INCOMING,
                    callType =
                        CallAttributesCompat
                            .CALL_TYPE_AUDIO_CALL,
                    callCapabilities = 0,
                    preferredStartingCallEndpoint =
                        null,
                    isLogExcluded = true
                )

            callsManager.addCall(
                callAttributes = attributes,
                onAnswer = {
                    activateIfNeeded(
                        context,
                        callId
                    )
                },
                onDisconnect = {
                    // A locked Priority call cannot be ended from the Watch
                    // or another local system surface. The paired phone owns
                    // termination and calls disconnectFromPhone().
                    throw IllegalStateException(
                        "Locked Priority call can only be ended by phone"
                    )
                },
                onSetActive = {
                    activateIfNeeded(
                        context,
                        callId
                    )
                },
                onSetInactive = {
                    throw IllegalStateException(
                        "Locked Priority call cannot be held on watch"
                    )
                }
            ) {
                synchronized(
                    PriorityTelecomController
                ) {
                    pendingCallId = null
                    session =
                        Session(
                            callId,
                            this
                        )
                }

                launch {
                    if (
                        synchronized(
                            PriorityTelecomController
                        ) {
                            cancelledBeforeAttach
                                .remove(callId)
                        }
                    ) {
                        disconnect(
                            DisconnectCause(
                                DisconnectCause.REMOTE
                            )
                        )
                        return@launch
                    }

                    when (
                        answer(
                            CallAttributesCompat
                                .CALL_TYPE_AUDIO_CALL
                        )
                    ) {
                        is CallControlResult.Success -> {
                            activateIfNeeded(
                                context,
                                callId
                            )
                            routeToWatchSpeaker()
                        }

                        is CallControlResult.Error -> {
                            synchronized(
                                PriorityTelecomController
                            ) {
                                fallbackToActivity
                                    .add(callId)
                            }
                            StateStore.setStatus(
                                context,
                                "Priority call · open watch to connect"
                            )
                            EventBus
                                .notifyStateChanged(
                                    context
                                )
                            runCatching {
                                disconnect(
                                    DisconnectCause(
                                        DisconnectCause
                                            .ERROR
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Telecom Priority auto-answer unavailable",
                e
            )
            synchronized(this) {
                fallbackToActivity.add(callId)
            }

            if (
                StateStore.incomingCall(context) ==
                    callId
            ) {
                StateStore.setStatus(
                    context,
                    "Priority call · open watch to connect"
                )
                EventBus.notifyStateChanged(
                    context
                )
            }
        } finally {
            val fallback =
                synchronized(this) {
                    fallbackToActivity
                        .remove(callId)
                }
            val expectedClose =
                synchronized(this) {
                    requestedClose
                        .remove(callId)
                }

            clearSession(callId)

            if (!fallback && !expectedClose) {
                handleUnexpectedSessionEnd(
                    context,
                    callId
                )
            }
        }
    }

    private suspend fun CallControlScope
        .routeToWatchSpeaker() {
        val endpoints =
            withTimeoutOrNull(
                SPEAKER_ROUTE_TIMEOUT_MS
            ) {
                availableEndpoints.first {
                    list ->
                    list.any {
                        endpoint ->
                        endpoint.type ==
                            CallEndpointCompat
                                .TYPE_SPEAKER
                    }
                }
            } ?: return

        val speaker =
            endpoints.firstOrNull {
                it.type ==
                    CallEndpointCompat.TYPE_SPEAKER
            } ?: return

        runCatching {
            requestEndpointChange(speaker)
        }.onFailure {
            Log.w(
                TAG,
                "Telecom speaker route request failed",
                it
            )
        }
    }

    private fun activateIfNeeded(
        context: Context,
        callId: String
    ) {
        if (
            synchronized(this) {
                cancelledBeforeAttach
                    .contains(callId)
            }
        ) {
            return
        }

        val incoming =
            StateStore.incomingCall(context)
        val active =
            StateStore.activeCall(context)

        if (
            active == callId &&
            incoming == null
        ) {
            return
        }

        if (
            incoming != callId &&
            active != callId
        ) {
            return
        }

        if (active != callId) {
            StateStore.setIncomingCall(
                context,
                null
            )
            StateStore.setCallInitiator(
                context,
                false
            )
            StateStore.setActiveCall(
                context,
                callId
            )
            StateStore.setActiveStartedAt(
                context,
                System.currentTimeMillis()
            )
            StateStore.clearReconnectWindow(
                context
            )
        }

        StateStore.setStatus(
            context,
            "Connecting live audio…"
        )
        AlertController.stop(context)
        EventBus.notifyStateChanged(context)

        LiveCallService.start(context)

        DataLayerTransport(context)
            .sendSignal(
                Protocol.CALL_ANSWER,
                callId
            ) {
                sent ->
                if (
                    !sent &&
                    StateStore.activeCall(
                        context
                    ) == callId
                ) {
                    CallHistoryStore.append(
                        context,
                        callId,
                        CallDirection.INCOMING,
                        CallOutcome.FAILED
                    )
                    StateStore.clearCallState(
                        context
                    )
                    StateStore.setStatus(
                        context,
                        "Phone is unreachable"
                    )
                    LiveCallAudio.stop(
                        context
                    )
                    LiveCallService.stop(
                        context
                    )
                    EventBus
                        .notifyStateChanged(
                            context
                        )
                    disconnectForFailure(
                        context,
                        callId
                    )
                }
            }
    }

    private fun handleUnexpectedSessionEnd(
        context: Context,
        callId: String
    ) {
        val incoming =
            StateStore.incomingCall(context) ==
                callId
        val active =
            StateStore.activeCall(context) ==
                callId

        if (!incoming && !active) return

        CallHistoryStore.append(
            context,
            callId,
            CallDirection.INCOMING,
            CallOutcome.DISCONNECTED,
            startedAt =
                if (active) {
                    StateStore.activeStartedAt(
                        context
                    )
                } else {
                    0L
                }
        )
        StateStore.clearCallState(context)
        StateStore.setPeerConnection(
            context,
            PeerConnectionState.DISCONNECTED
        )
        StateStore.setPeerRoute(
            context,
            PeerRoute.OFFLINE
        )
        StateStore.setStatus(
            context,
            "Priority call disconnected · TALK recommended"
        )
        AlertController.stop(context)
        LiveCallAudio.stop(context)
        CallHaptics.ended(
            context,
            callId
        )
        LiveCallService.stop(context)
        EventBus.notifyStateChanged(context)

        DataLayerTransport(context)
            .sendSignal(
                Protocol.CALL_DISCONNECTED,
                callId
            ) { }
    }

    private fun clearSession(
        callId: String
    ) {
        synchronized(this) {
            if (pendingCallId == callId) {
                pendingCallId = null
            }
            if (session?.callId == callId) {
                session = null
            }
            cancelledBeforeAttach.remove(callId)
        }
    }
}
