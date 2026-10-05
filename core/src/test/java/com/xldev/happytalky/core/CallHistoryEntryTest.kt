package com.xldev.happytalky.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CallHistoryEntryTest {
    @Test
    fun completedCallFormatsDuration() {
        val entry =
            CallHistoryEntry(
                id = "entry",
                callId = "call",
                occurredAt = 0L,
                direction =
                    CallDirection.OUTGOING,
                outcome =
                    CallOutcome.COMPLETED,
                durationMs = 74_000L,
            )

        assertEquals(
            "1:14",
            entry.displayDuration()
        )
        assertEquals(
            "Call · 1:14",
            entry.shortLabel()
        )
    }

    @Test
    fun declineLabelsPreserveWhoDeclined() {
        val mine =
            CallHistoryEntry(
                id = "mine",
                callId = "call-1",
                occurredAt = 0L,
                direction =
                    CallDirection.INCOMING,
                outcome =
                    CallOutcome.DECLINED_BY_ME,
            )
        val peer =
            CallHistoryEntry(
                id = "peer",
                callId = "call-2",
                occurredAt = 0L,
                direction =
                    CallDirection.OUTGOING,
                outcome =
                    CallOutcome.DECLINED_BY_PEER,
            )

        assertEquals(
            "Declined call",
            mine.shortLabel()
        )
        assertEquals(
            "Call declined",
            peer.shortLabel()
        )
    }
    @Test
    fun priorityCallLabelsAreExplicit() {
        val completed =
            CallHistoryEntry(
                id = "priority",
                callId = "priority-call",
                occurredAt = 0L,
                direction =
                    CallDirection.OUTGOING,
                outcome =
                    CallOutcome.COMPLETED,
                durationMs = 15_000L,
                mode = CallMode.PRIORITY,
            )

        assertEquals(
            "Priority call · 0:15",
            completed.shortLabel()
        )
    }

}
