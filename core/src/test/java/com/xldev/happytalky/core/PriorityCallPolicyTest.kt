package com.xldev.happytalky.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityCallPolicyTest {
    @Test
    fun phoneCanRequestLockedPriorityImmediately() {
        assertTrue(
            PriorityCallPolicy.canRequestLocked(
                localRole = EndpointRole.PHONE,
                callInProgress = false,
                recording = false,
                peerConnected = true,
                peerSupportsLockedPriority = true
            )
        )

        assertFalse(
            PriorityCallPolicy.canRequestLocked(
                localRole = EndpointRole.PHONE,
                callInProgress = true,
                recording = false,
                peerConnected = true,
                peerSupportsLockedPriority = true
            )
        )

        assertFalse(
            PriorityCallPolicy.canRequestLocked(
                localRole = EndpointRole.PHONE,
                callInProgress = false,
                recording = false,
                peerConnected = true,
                peerSupportsLockedPriority = false
            )
        )
    }

    @Test
    fun lockedPriorityAutoAnswersWhenWatchActivityIsVisible() {
        assertTrue(
            PriorityCallPolicy.canAutoAnswer(
                localRole = EndpointRole.WATCH,
                locked = true,
                legacyEnabled = false,
                mode = CallMode.PRIORITY,
                incomingCallPresent = true,
                activityVisible = true
            )
        )

        assertFalse(
            PriorityCallPolicy.canAutoAnswer(
                localRole = EndpointRole.WATCH,
                locked = true,
                legacyEnabled = false,
                mode = CallMode.PRIORITY,
                incomingCallPresent = true,
                activityVisible = false
            )
        )
    }

    @Test
    fun legacyPriorityStillRequiresOptIn() {
        assertFalse(
            PriorityCallPolicy.canAutoAnswer(
                localRole = EndpointRole.WATCH,
                locked = false,
                legacyEnabled = false,
                mode = CallMode.PRIORITY,
                incomingCallPresent = true,
                activityVisible = true
            )
        )

        assertTrue(
            PriorityCallPolicy.canAutoAnswer(
                localRole = EndpointRole.WATCH,
                locked = false,
                legacyEnabled = true,
                mode = CallMode.PRIORITY,
                incomingCallPresent = true,
                activityVisible = true
            )
        )
    }

    @Test
    fun lockedPriorityCanUseTelecomWithoutVisibleActivity() {
        assertTrue(
            PriorityCallPolicy
                .canUseTelecomAutoAnswer(
                    localRole =
                        EndpointRole.WATCH,
                    locked = true,
                    mode = CallMode.PRIORITY,
                    incomingCallPresent = true,
                    telecomAvailable = true
                )
        )

        assertFalse(
            PriorityCallPolicy
                .canUseTelecomAutoAnswer(
                    localRole =
                        EndpointRole.WATCH,
                    locked = true,
                    mode = CallMode.PRIORITY,
                    incomingCallPresent = true,
                    telecomAvailable = false
                )
        )

        assertFalse(
            PriorityCallPolicy
                .canUseTelecomAutoAnswer(
                    localRole =
                        EndpointRole.PHONE,
                    locked = true,
                    mode = CallMode.PRIORITY,
                    incomingCallPresent = true,
                    telecomAvailable = true
                )
        )
    }

    @Test
    fun watchCannotTerminateLockedPriorityCall() {
        assertFalse(
            PriorityCallPolicy.canLocalTerminate(
                localRole = EndpointRole.WATCH,
                locked = true
            )
        )
        assertTrue(
            PriorityCallPolicy.canLocalTerminate(
                localRole = EndpointRole.PHONE,
                locked = true
            )
        )
        assertTrue(
            PriorityCallPolicy.canLocalTerminate(
                localRole = EndpointRole.WATCH,
                locked = false
            )
        )
    }

    @Test
    fun sameActiveCallIgnoresLatePriorityInsteadOfBusy() {
        assertEquals(
            PriorityRequestDisposition
                .IGNORE_ALREADY_ACTIVE,
            PriorityCallPolicy
                .requestDisposition(
                    requestedCallId = "call-1",
                    incomingCallId = null,
                    outgoingCallId = null,
                    activeCallId = "call-1"
                )
        )
    }

    @Test
    fun unrelatedActiveCallRejectsPriorityAsBusy() {
        assertEquals(
            PriorityRequestDisposition
                .REJECT_BUSY,
            PriorityCallPolicy
                .requestDisposition(
                    requestedCallId = "call-1",
                    incomingCallId = null,
                    outgoingCallId = null,
                    activeCallId = "call-2"
                )
        )
    }

    @Test
    fun phoneCancelStillClosesLockedWatchAfterAutoAnswerRace() {
        assertTrue(
            PriorityCallPolicy
                .acceptsPhoneCancelOnWatch(
                    localRole =
                        EndpointRole.WATCH,
                    locked = true,
                    incomingMatches = false,
                    activeMatches = true
                )
        )

        assertFalse(
            PriorityCallPolicy
                .acceptsPhoneCancelOnWatch(
                    localRole =
                        EndpointRole.WATCH,
                    locked = false,
                    incomingMatches = false,
                    activeMatches = true
                )
        )
    }

}
