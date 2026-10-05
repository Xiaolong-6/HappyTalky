package com.xldev.happytalky.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

object AudioPlayer {
    private var player: MediaPlayer? = null
    private var playingPath: String? = null

    /** Null means this file is not currently playing (including completion/error). */
    @Synchronized
    fun progressFor(file: File): Float? {
        if (playingPath != file.absolutePath) return null
        val current = player ?: return null
        return runCatching {
            if (!current.isPlaying || current.duration <= 0) null
            else (current.currentPosition.toFloat() / current.duration).coerceIn(0f, 1f)
        }.getOrNull()
    }

    @Synchronized
    fun play(
        context: Context,
        file: File,
        deleteAfter: Boolean = false,
        onCompleted: () -> Unit = {}
    ) {
        stop()
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        playingPath = file.absolutePath

        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        AudioAttributes.USAGE_MEDIA
                    )
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SPEECH
                    )
                    .build()
            )
            mediaPlayer.setDataSource(
                file.absolutePath
            )
            mediaPlayer.setOnCompletionListener {
                synchronized(this) {
                    runCatching {
                        it.release()
                    }
                    if (player === it) {
                        player = null
                    }
                    if (deleteAfter) {
                        file.delete()
                    }
                }
                onCompleted()
            }
            mediaPlayer.setOnErrorListener {
                    mp,
                    _,
                    _ ->
                synchronized(this) {
                    runCatching {
                        mp.release()
                    }
                    if (player === mp) {
                        player = null
                    }
                    if (deleteAfter) {
                        file.delete()
                    }
                }
                true
            }
            mediaPlayer.prepare()
            mediaPlayer.start()
        } catch (_: Exception) {
            runCatching {
                mediaPlayer.release()
            }
            if (player === mediaPlayer) {
                player = null
            }
            if (deleteAfter) {
                file.delete()
            }
        }
    }

    @Synchronized
    fun stop() {
        playingPath = null
        val current = player ?: return
        player = null
        runCatching {
            current.stop()
        }
        runCatching {
            current.release()
        }
    }
}
