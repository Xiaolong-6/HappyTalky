package com.xldev.happytalky.core

import android.content.Context
import android.media.MediaRecorder
import java.io.File
import java.util.UUID

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    @Suppress("DEPRECATION")
    fun start(): Boolean {
        if (recorder != null) return false

        val dir = File(context.cacheDir, "outgoing-voice").apply { mkdirs() }
        val file = File(dir, "voice-" + UUID.randomUUID() + ".m4a")

        val mediaRecorder =
            if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context)
            else MediaRecorder()

        return try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioChannels(1)
            mediaRecorder.setAudioSamplingRate(16_000)
            mediaRecorder.setAudioEncodingBitRate(32_000)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            outputFile = file
            true
        } catch (_: Exception) {
            runCatching { mediaRecorder.release() }
            file.delete()
            false
        }
    }

    fun stop(): File? {
        val current = recorder ?: return null
        val file = outputFile
        recorder = null
        outputFile = null

        return try {
            current.stop()
            current.release()
            if (file != null && file.exists() && file.length() > 0L) file else null
        } catch (_: Exception) {
            runCatching { current.release() }
            file?.delete()
            null
        }
    }

    fun cancel() {
        val current = recorder
        recorder = null
        val file = outputFile
        outputFile = null
        runCatching { current?.stop() }
        runCatching { current?.release() }
        file?.delete()
    }
}
