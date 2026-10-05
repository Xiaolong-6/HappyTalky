package com.xldev.happytalky.wear

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

internal class WearTextToSpeechController(
    context: Context,
    private val onSpeakingItemChanged: (String?) -> Unit,
) {
    private data class PendingSpeech(
        val itemId: String,
        val text: String,
    )

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private var engine: TextToSpeech? = null
    private var ready = false
    private var pendingSpeech: PendingSpeech? = null
    private var activeItemId: String? = null
    private var activeUtteranceId: String? = null
    private var utteranceSequence = 0L

    init {
        engine =
            TextToSpeech(
                context.applicationContext
            ) { status ->
                val tts = engine
                if (
                    status !=
                        TextToSpeech.SUCCESS ||
                    tts == null
                ) {
                    ready = false
                    clearSpeakingState()
                    return@TextToSpeech
                }

                ready = true
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            AudioAttributes.USAGE_MEDIA
                        )
                        .setContentType(
                            AudioAttributes.CONTENT_TYPE_SPEECH
                        )
                        .build()
                )

                val locale = Locale.getDefault()
                if (
                    tts.isLanguageAvailable(locale) >=
                        TextToSpeech.LANG_AVAILABLE
                ) {
                    tts.language = locale
                }

                tts.setOnUtteranceProgressListener(
                    object :
                        UtteranceProgressListener() {
                        override fun onStart(
                            utteranceId: String?
                        ) = Unit

                        override fun onDone(
                            utteranceId: String?
                        ) {
                            finishIfCurrent(
                                utteranceId
                            )
                        }

                        @Deprecated(
                            "Deprecated in Android"
                        )
                        override fun onError(
                            utteranceId: String?
                        ) {
                            finishIfCurrent(
                                utteranceId
                            )
                        }

                        override fun onError(
                            utteranceId: String?,
                            errorCode: Int
                        ) {
                            finishIfCurrent(
                                utteranceId
                            )
                        }

                        override fun onStop(
                            utteranceId: String?,
                            interrupted: Boolean
                        ) {
                            finishIfCurrent(
                                utteranceId
                            )
                        }
                    }
                )

                pendingSpeech
                    ?.also {
                        pendingSpeech = null
                    }
                    ?.let(::speakNow)
            }
    }

    fun toggle(
        itemId: String,
        text: String,
    ) {
        if (text.isBlank()) {
            return
        }

        when (
            textSpeechCommand(
                activeItemId,
                itemId
            )
        ) {
            TextSpeechCommand.STOP ->
                stop()

            TextSpeechCommand.SPEAK -> {
                val speech =
                    PendingSpeech(
                        itemId = itemId,
                        text = text,
                    )

                activeItemId = itemId
                notifySpeakingItem(itemId)

                if (ready) {
                    speakNow(speech)
                } else {
                    pendingSpeech = speech
                }
            }
        }
    }

    fun stop() {
        pendingSpeech = null
        activeUtteranceId = null
        engine?.stop()
        clearSpeakingState()
    }

    fun shutdown() {
        stop()
        ready = false
        engine?.shutdown()
        engine = null
    }

    private fun speakNow(
        speech: PendingSpeech
    ) {
        val tts =
            engine
                ?: run {
                    clearSpeakingState()
                    return
                }

        tts.stop()

        val utteranceId =
            "happytalky_text_" +
                (++utteranceSequence)

        activeItemId = speech.itemId
        activeUtteranceId = utteranceId
        notifySpeakingItem(
            speech.itemId
        )

        val result =
            tts.speak(
                speech.text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )

        if (
            result ==
                TextToSpeech.ERROR
        ) {
            clearSpeakingState()
        }
    }

    private fun finishIfCurrent(
        utteranceId: String?
    ) {
        if (
            utteranceId == null ||
            utteranceId !=
                activeUtteranceId
        ) {
            return
        }

        activeUtteranceId = null
        clearSpeakingState()
    }

    private fun clearSpeakingState() {
        activeItemId = null
        mainHandler.post {
            onSpeakingItemChanged(
                null
            )
        }
    }

    private fun notifySpeakingItem(
        itemId: String
    ) {
        mainHandler.post {
            onSpeakingItemChanged(
                itemId
            )
        }
    }
}
