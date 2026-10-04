package com.xiaolong.happytalky.wear

internal enum class TextSpeechCommand {
    SPEAK,
    STOP,
}

internal fun textSpeechCommand(
    activeItemId: String?,
    requestedItemId: String,
): TextSpeechCommand =
    if (
        activeItemId ==
            requestedItemId
    ) {
        TextSpeechCommand.STOP
    } else {
        TextSpeechCommand.SPEAK
    }
