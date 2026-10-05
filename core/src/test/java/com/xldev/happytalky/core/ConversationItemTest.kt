package com.xldev.happytalky.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationItemTest {
    @Test
    fun roomEntityRoundTripsVoiceMetadata() {
        val item =
            ConversationItem(
                id = "voice-1",
                type = ConversationItemType.VOICE,
                direction = ConversationDirection.INCOMING,
                createdAt = 1234L,
                readAt = null,
                deliveryState = DeliveryState.DELIVERED,
                audioFileName = "1234-in-voice-1.m4a",
                durationMs = 3200L
            )

        assertEquals(
            item,
            item.toEntity().toModelOrNull()
        )
    }

    @Test
    fun roomEntityRoundTripsPriorityCallMetadata() {
        val item =
            ConversationItem(
                id = "call-entry",
                type = ConversationItemType.CALL,
                direction = ConversationDirection.OUTGOING,
                createdAt = 2000L,
                readAt = 2000L,
                deliveryState = DeliveryState.READ,
                durationMs = 15000L,
                callId = "call-1",
                callOutcome = CallOutcome.COMPLETED.name,
                callMode = CallMode.PRIORITY,
                startedAt = 2000L,
                endedAt = 17000L
            )

        assertEquals(
            item,
            item.toEntity().toModelOrNull()
        )
    }

    @Test
    fun roomEntityRoundTripsTextMetadata() {
        val item =
            ConversationItem(
                id = "text-1",
                type = ConversationItemType.TEXT,
                direction = ConversationDirection.OUTGOING,
                createdAt = 4_000L,
                deliveryState = DeliveryState.QUEUED,
                text = "Coming! 👍"
            )

        assertEquals(
            item,
            item.toEntity().toModelOrNull()
        )
    }

    @Test
    fun malformedPersistedEnumIsIgnored() {
        val entity =
            ConversationEntity(
                id = "bad",
                type = "UNKNOWN",
                direction =
                    ConversationDirection.INCOMING.name,
                createdAt = 1L,
                readAt = null,
                deliveryState =
                    DeliveryState.LOCAL.name,
                text = null,
                audioFileName = null,
                durationMs = 0L,
                callId = null,
                callOutcome = null,
                callMode = null,
                startedAt = null,
                endedAt = null
            )

        assertNull(
            entity.toModelOrNull()
        )
    }
}
