package com.xldev.happytalky.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_ANSWER -> answer(appContext)
            ACTION_DECLINE -> decline(appContext)
            ACTION_HANG_UP -> hangUp(appContext)
        }
    }

    private fun answer(context: Context) {
        val callId = StateStore.incomingCall(context) ?: return

        AlertController.stop(context)
        StateStore.setIncomingCall(context, null)
        StateStore.setCallInitiator(context, false)
        StateStore.setActiveCall(context, callId)
        StateStore.setActiveStartedAt(
            context,
            System.currentTimeMillis()
        )
        StateStore.clearReconnectWindow(context)
        StateStore.setStatus(context, "Connecting live audio…")
        LiveCallService.start(context)
        EventBus.notifyStateChanged(context)

        DataLayerTransport(context)
            .sendSignal(Protocol.CALL_ANSWER, callId) { sent ->
                if (!sent && StateStore.activeCall(context) == callId) {
                    CallHistoryStore.append(
                        context,
                        callId,
                        CallDirection.INCOMING,
                        CallOutcome.FAILED
                    )
                    StateStore.clearCallState(context)
                    StateStore.setStatus(context, "Peer is unreachable")
                    LiveCallService.stop(context)
                    EventBus.notifyStateChanged(context)
                }
            }
    }

    private fun decline(context: Context) {
        val callId = StateStore.incomingCall(context) ?: return

        if (
            !PriorityCallPolicy.canLocalTerminate(
                localRole =
                    EndpointRole.fromContext(
                        context
                    ),
                locked =
                    StateStore.priorityLocked(
                        context
                    )
            )
        ) {
            return
        }

        CallHistoryStore.append(
            context,
            callId,
            CallDirection.INCOMING,
            CallOutcome.DECLINED_BY_ME
        )
        StateStore.clearCallState(context)
        StateStore.setStatus(context, "Call declined")
        AlertController.stop(context)
        EventBus.notifyStateChanged(context)

        DataLayerTransport(context)
            .sendSignal(Protocol.CALL_DECLINE, callId) { }
    }

    private fun hangUp(context: Context) {
        if (
            !PriorityCallPolicy.canLocalTerminate(
                localRole =
                    EndpointRole.fromContext(
                        context
                    ),
                locked =
                    StateStore.priorityLocked(
                        context
                    )
            )
        ) {
            return
        }

        val active = StateStore.activeCall(context)
        val outgoing = StateStore.outgoingCall(context)
        val incoming = StateStore.incomingCall(context)
        val callId = active ?: outgoing ?: incoming ?: return

        val path = when {
            active != null -> Protocol.CALL_END
            outgoing != null -> Protocol.CALL_CANCEL
            else -> Protocol.CALL_DECLINE
        }

        when {
            active != null -> {
                CallHistoryStore.append(
                    context,
                    callId,
                    if (
                        StateStore.callInitiator(
                            context
                        )
                    ) {
                        CallDirection.OUTGOING
                    } else {
                        CallDirection.INCOMING
                    },
                    CallOutcome.COMPLETED,
                    startedAt =
                        StateStore.activeStartedAt(
                            context
                        )
                )
            }

            outgoing != null -> {
                CallHistoryStore.append(
                    context,
                    callId,
                    CallDirection.OUTGOING,
                    CallOutcome.CANCELLED_BY_ME
                )
            }

            else -> {
                CallHistoryStore.append(
                    context,
                    callId,
                    CallDirection.INCOMING,
                    CallOutcome.DECLINED_BY_ME
                )
            }
        }

        StateStore.clearCallState(context)
        StateStore.setStatus(
            context,
            if (outgoing != null) "Call cancelled" else "Call ended"
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
            .sendSignal(path, callId) { }
    }

    companion object {
        const val ACTION_ANSWER =
            "com.xldev.happytalky.action.ANSWER_CALL"
        const val ACTION_DECLINE =
            "com.xldev.happytalky.action.DECLINE_CALL"
        const val ACTION_HANG_UP =
            "com.xldev.happytalky.action.HANG_UP"
    }
}
