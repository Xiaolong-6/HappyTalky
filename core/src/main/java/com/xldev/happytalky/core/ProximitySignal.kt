package com.xldev.happytalky.core

import java.security.SecureRandom
import java.util.ArrayDeque
import kotlin.math.roundToInt

enum class ProximityBand {
    FAR,
    NEARBY,
    CLOSE,
    VERY_CLOSE
}

enum class ProximityTrend {
    GETTING_CLOSER,
    STEADY,
    GETTING_FARTHER
}

data class ProximityReading(
    val rawRssi: Int,
    val filteredRssi: Double,
    val signalScore: Int,
    val band: ProximityBand,
    val trend: ProximityTrend,
    val timestampMs: Long
)

class ProximitySignalProcessor(
    private val medianWindowSize: Int = 5,
    private val emaAlpha: Double = 0.35,
    private val trendAlpha: Double = 0.12
) {
    private val recent = ArrayDeque<Int>()
    private var filteredRssi: Double? = null
    private var trendReference: Double? = null
    private var sampleCount = 0

    fun reset() {
        recent.clear()
        filteredRssi = null
        trendReference = null
        sampleCount = 0
    }

    fun add(
        rawRssi: Int,
        timestampMs: Long
    ): ProximityReading {
        recent.addLast(rawRssi)
        while (recent.size > medianWindowSize) {
            recent.removeFirst()
        }

        val sorted = recent.toList().sorted()
        val middle = sorted.size / 2
        val median =
            if (sorted.size % 2 == 1) {
                sorted[middle].toDouble()
            } else {
                (
                    sorted[middle - 1] +
                        sorted[middle]
                    ) / 2.0
            }

        val filtered =
            filteredRssi?.let {
                it +
                    emaAlpha *
                    (median - it)
            } ?: median
        filteredRssi = filtered

        val reference =
            trendReference?.let {
                it +
                    trendAlpha *
                    (filtered - it)
            } ?: filtered
        trendReference = reference
        sampleCount += 1

        val delta = filtered - reference
        val trend =
            if (sampleCount < 4) {
                ProximityTrend.STEADY
            } else {
                when {
                    delta >= 2.5 ->
                        ProximityTrend.GETTING_CLOSER

                    delta <= -2.5 ->
                        ProximityTrend.GETTING_FARTHER

                    else ->
                        ProximityTrend.STEADY
                }
            }

        val score =
            (
                (filtered + 100.0) /
                    55.0 *
                    100.0
                )
                .roundToInt()
                .coerceIn(0, 100)

        val band =
            when {
                filtered < -82.0 ->
                    ProximityBand.FAR

                filtered < -70.0 ->
                    ProximityBand.NEARBY

                filtered < -58.0 ->
                    ProximityBand.CLOSE

                else ->
                    ProximityBand.VERY_CLOSE
            }

        return ProximityReading(
            rawRssi = rawRssi,
            filteredRssi = filtered,
            signalScore = score,
            band = band,
            trend = trend,
            timestampMs = timestampMs
        )
    }
}

object ProximitySessionToken {
    private const val BYTE_COUNT = 8
    private val random = SecureRandom()

    fun create(): String {
        val bytes = ByteArray(BYTE_COUNT)
        random.nextBytes(bytes)
        return bytes.joinToString("") {
            (it.toInt() and 0xff)
                .toString(16)
                .padStart(2, '0')
        }
    }

    fun decode(token: String): ByteArray? {
        if (token.length != BYTE_COUNT * 2) {
            return null
        }

        return runCatching {
            ByteArray(BYTE_COUNT) { index ->
                token.substring(
                    index * 2,
                    index * 2 + 2
                )
                    .toInt(16)
                    .toByte()
            }
        }.getOrNull()
    }

    fun isValid(token: String): Boolean =
        decode(token) != null
}
