package com.xldev.happytalky.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextMessageStoreTest {
    @Test
    fun normalizeTrimsAndKeepsEmoji() {
        assertEquals(
            "hello 😊",
            TextMessageStore.normalize(
                "  hello 😊  "
            )
        )
    }

    @Test
    fun normalizeRejectsBlankText() {
        assertNull(
            TextMessageStore.normalize(
                "   "
            )
        )
    }

    @Test
    fun normalizeCapsWireLength() {
        val raw =
            "x".repeat(
                Protocol.MAX_TEXT_LENGTH +
                    25
            )
        val normalized =
            TextMessageStore.normalize(
                raw
            )
                ?: error("missing text")

        assertEquals(
            Protocol.MAX_TEXT_LENGTH,
            normalized.codePointCount(
                0,
                normalized.length
            )
        )
    }

    @Test
    fun limitDoesNotSplitEmojiSurrogates() {
        val raw =
            "😊".repeat(
                Protocol.MAX_TEXT_LENGTH +
                    1
            )
        val limited =
            TextMessageStore.limit(
                raw
            )

        assertEquals(
            Protocol.MAX_TEXT_LENGTH,
            limited.codePointCount(
                0,
                limited.length
            )
        )
        assertEquals(
            "😊",
            limited.takeLast(2)
        )
    }
}
