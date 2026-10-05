package com.xldev.happytalky.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMessageTest {
    @Test
    fun durationFormatsLikeVoiceMessageBubble() {
        val message =
            VoiceMessage(
                id = "sample",
                createdAt = 0L,
                direction =
                    VoiceDirection.INCOMING,
                file =
                    File("sample.m4a"),
                durationMs = 65_100L,
            )

        assertEquals(
            "1:05",
            message.displayDuration()
        )
    }

    @Test
    fun incomingReadStateDependsOnReadTimestamp() {
        val unread =
            VoiceMessage(
                id = "in-unread",
                createdAt = 1L,
                direction =
                    VoiceDirection.INCOMING,
                file =
                    File("unread.m4a"),
            )
        val read =
            unread.copy(
                readAt = 2L
            )
        val outgoing =
            unread.copy(
                id = "out",
                direction =
                    VoiceDirection.OUTGOING,
            )

        assertFalse(unread.isRead)
        assertTrue(read.isRead)
        assertTrue(outgoing.isRead)
    }

    @Test
    fun subSecondDurationRoundsToOneSecond() {
        val message =
            VoiceMessage(
                id = "sample",
                createdAt = 0L,
                direction =
                    VoiceDirection.OUTGOING,
                file =
                    File("sample.m4a"),
                durationMs = 700L,
            )

        assertEquals(
            "0:01",
            message.displayDuration()
        )
    }
}
