package com.xldev.happytalky.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CallSignalOutcomePolicyTest {
    @Test
    fun normalEndOfActiveCallIsCompleted() {
        assertEquals(
            CallOutcome.COMPLETED,
            CallSignalOutcomePolicy
                .remoteTerminalOutcome(
                    wasActive = true,
                    disconnected = false
                )
        )
    }

    @Test
    fun abnormalEndOfActiveCallIsDisconnected() {
        assertEquals(
            CallOutcome.DISCONNECTED,
            CallSignalOutcomePolicy
                .remoteTerminalOutcome(
                    wasActive = true,
                    disconnected = true
                )
        )
    }

    @Test
    fun normalEndBeforeAnswerIsCancelledByPeer() {
        assertEquals(
            CallOutcome.CANCELLED_BY_PEER,
            CallSignalOutcomePolicy
                .remoteTerminalOutcome(
                    wasActive = false,
                    disconnected = false
                )
        )
    }
}
