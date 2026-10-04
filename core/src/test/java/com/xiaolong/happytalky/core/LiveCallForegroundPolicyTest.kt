package com.xiaolong.happytalky.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveCallForegroundPolicyTest {
    @Test
    fun normalCallKeepsMicrophoneForegroundType() {
        assertEquals(
            LiveCallForegroundTypes(
                microphone = true,
                phoneCall = false
            ),
            LiveCallForegroundPolicy.forCall(
                isWatch = true,
                telecomManaged = false
            )
        )
    }

    @Test
    fun telecomPriorityKeepsMicrophoneAndAddsPhoneCallType() {
        assertEquals(
            LiveCallForegroundTypes(
                microphone = true,
                phoneCall = true
            ),
            LiveCallForegroundPolicy.forCall(
                isWatch = true,
                telecomManaged = true
            )
        )
    }

    @Test
    fun phoneNeverAddsWatchTelecomType() {
        assertEquals(
            LiveCallForegroundTypes(
                microphone = true,
                phoneCall = false
            ),
            LiveCallForegroundPolicy.forCall(
                isWatch = false,
                telecomManaged = true
            )
        )
    }
}
