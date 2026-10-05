package com.xldev.happytalky.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximitySignalProcessorTest {
    @Test
    fun classifiesQualitativeBandsWithoutDistanceClaims() {
        val processor =
            ProximitySignalProcessor()

        assertEquals(
            ProximityBand.FAR,
            processor.add(
                rawRssi = -90,
                timestampMs = 1L
            ).band
        )

        processor.reset()
        assertEquals(
            ProximityBand.NEARBY,
            processor.add(
                rawRssi = -76,
                timestampMs = 2L
            ).band
        )

        processor.reset()
        assertEquals(
            ProximityBand.CLOSE,
            processor.add(
                rawRssi = -64,
                timestampMs = 3L
            ).band
        )

        processor.reset()
        assertEquals(
            ProximityBand.VERY_CLOSE,
            processor.add(
                rawRssi = -50,
                timestampMs = 4L
            ).band
        )
    }

    @Test
    fun medianWindowRejectsSingleStrongSpike() {
        val processor =
            ProximitySignalProcessor()

        repeat(5) {
            processor.add(
                rawRssi = -80,
                timestampMs =
                    it.toLong()
            )
        }

        val reading =
            processor.add(
                rawRssi = -40,
                timestampMs = 10L
            )

        assertTrue(
            reading.filteredRssi < -70.0
        )
    }

    @Test
    fun sustainedImprovementReportsGettingCloser() {
        val processor =
            ProximitySignalProcessor()
        var reading =
            processor.add(
                rawRssi = -90,
                timestampMs = 0L
            )

        listOf(
            -86,
            -82,
            -76,
            -70,
            -64,
            -58
        ).forEachIndexed {
                index,
                rssi ->
            reading =
                processor.add(
                    rawRssi = rssi,
                    timestampMs =
                        (index + 1)
                            .toLong()
                )
        }

        assertEquals(
            ProximityTrend.GETTING_CLOSER,
            reading.trend
        )
    }

    @Test
    fun sessionTokenRoundTripsToEightBytes() {
        val token =
            ProximitySessionToken.create()
        val decoded =
            ProximitySessionToken.decode(
                token
            )

        assertEquals(16, token.length)
        assertNotNull(decoded)
        assertEquals(
            8,
            decoded?.size
        )
        assertNull(
            ProximitySessionToken.decode(
                "not-a-token"
            )
        )
    }
}
