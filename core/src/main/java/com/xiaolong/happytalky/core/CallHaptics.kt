package com.xiaolong.happytalky.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Short call-state haptics shared by Phone and Watch.
 *
 * A call only gets an "ended" haptic after this process observed its live
 * audio attach, so rejected, missed, failed-to-connect, and cancelled-before-
 * answer calls do not masquerade as completed calls.
 */
object CallHaptics {
    private var connectedCallId: String? = null

    @Synchronized
    fun connected(
        context: Context,
        callId: String
    ) {
        if (
            callId.isBlank() ||
            connectedCallId == callId
        ) {
            return
        }

        connectedCallId = callId
        vibrate(
            context,
            predefinedEffect =
                if (
                    Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
                ) {
                    VibrationEffect.EFFECT_HEAVY_CLICK
                } else {
                    null
                },
            fallbackDurationMs = 90L
        )
    }

    @Synchronized
    fun ended(
        context: Context,
        callId: String
    ) {
        if (
            callId.isBlank() ||
            connectedCallId != callId
        ) {
            return
        }

        connectedCallId = null
        vibrate(
            context,
            predefinedEffect =
                if (
                    Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
                ) {
                    VibrationEffect.EFFECT_DOUBLE_CLICK
                } else {
                    null
                },
            fallbackDurationMs = 120L
        )
    }

    private fun vibrate(
        context: Context,
        predefinedEffect: Int?,
        fallbackDurationMs: Long
    ) {
        val vibrator =
            if (
                Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S
            ) {
                context
                    .getSystemService(
                        VibratorManager::class.java
                    )
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(
                    Context.VIBRATOR_SERVICE
                ) as? Vibrator
            }
                ?: return

        if (!vibrator.hasVibrator()) return

        val effect =
            if (
                predefinedEffect != null &&
                Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
            ) {
                VibrationEffect.createPredefined(
                    predefinedEffect
                )
            } else {
                VibrationEffect.createOneShot(
                    fallbackDurationMs,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            }

        runCatching {
            vibrator.vibrate(effect)
        }
    }
}
