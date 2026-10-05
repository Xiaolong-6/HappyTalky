package com.xldev.happytalky.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class WearTextSpeechPolicyTest {
    @Test
    fun inactiveMessageStartsSpeech() {
        assertEquals(
            TextSpeechCommand.SPEAK,
            textSpeechCommand(
                activeItemId = null,
                requestedItemId =
                    "message-1",
            )
        )
    }

    @Test
    fun sameMessageStopsSpeech() {
        assertEquals(
            TextSpeechCommand.STOP,
            textSpeechCommand(
                activeItemId =
                    "message-1",
                requestedItemId =
                    "message-1",
            )
        )
    }

    @Test
    fun differentMessageReplacesSpeech() {
        assertEquals(
            TextSpeechCommand.SPEAK,
            textSpeechCommand(
                activeItemId =
                    "message-1",
                requestedItemId =
                    "message-2",
            )
        )
    }
}
