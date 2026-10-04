package com.xiaolong.happytalky.core

data class LiveCallForegroundTypes(
    val microphone: Boolean,
    val phoneCall: Boolean
)

object LiveCallForegroundPolicy {
    fun forCall(
        isWatch: Boolean,
        telecomManaged: Boolean
    ): LiveCallForegroundTypes =
        LiveCallForegroundTypes(
            microphone = true,
            phoneCall =
                isWatch &&
                    telecomManaged
        )
}
