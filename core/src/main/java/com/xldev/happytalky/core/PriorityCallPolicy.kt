package com.xldev.happytalky.core

enum class PriorityRequestDisposition {
    APPLY,
    IGNORE_ALREADY_ACTIVE,
    REJECT_BUSY
}

object PriorityCallPolicy {
    fun canRequestLocked(
        localRole: EndpointRole,
        callInProgress: Boolean,
        recording: Boolean,
        peerConnected: Boolean,
        peerSupportsLockedPriority: Boolean
    ): Boolean =
        localRole == EndpointRole.PHONE &&
            !callInProgress &&
            !recording &&
            peerConnected &&
            peerSupportsLockedPriority

    fun requestDisposition(
        requestedCallId: String,
        incomingCallId: String?,
        outgoingCallId: String?,
        activeCallId: String?
    ): PriorityRequestDisposition =
        when {
            activeCallId ==
                requestedCallId ->
                PriorityRequestDisposition
                    .IGNORE_ALREADY_ACTIVE

            activeCallId != null ||
                outgoingCallId != null ||
                (
                    incomingCallId != null &&
                    incomingCallId !=
                        requestedCallId
                ) ->
                PriorityRequestDisposition
                    .REJECT_BUSY

            else ->
                PriorityRequestDisposition.APPLY
        }

    fun canAutoAnswer(
        localRole: EndpointRole,
        locked: Boolean,
        legacyEnabled: Boolean,
        mode: CallMode,
        incomingCallPresent: Boolean,
        activityVisible: Boolean
    ): Boolean =
        localRole == EndpointRole.WATCH &&
            mode == CallMode.PRIORITY &&
            incomingCallPresent &&
            activityVisible &&
            (
                locked ||
                    legacyEnabled
            )

    fun canUseTelecomAutoAnswer(
        localRole: EndpointRole,
        locked: Boolean,
        mode: CallMode,
        incomingCallPresent: Boolean,
        telecomAvailable: Boolean
    ): Boolean =
        localRole == EndpointRole.WATCH &&
            locked &&
            mode == CallMode.PRIORITY &&
            incomingCallPresent &&
            telecomAvailable

    fun canLocalTerminate(
        localRole: EndpointRole,
        locked: Boolean
    ): Boolean =
        !(
            localRole == EndpointRole.WATCH &&
                locked
            )

    fun acceptsPhoneCancelOnWatch(
        localRole: EndpointRole,
        locked: Boolean,
        incomingMatches: Boolean,
        activeMatches: Boolean
    ): Boolean =
        incomingMatches ||
            (
                localRole ==
                    EndpointRole.WATCH &&
                    locked &&
                    activeMatches
                )
}
