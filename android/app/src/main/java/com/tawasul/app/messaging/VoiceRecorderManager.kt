package com.tawasul.app.messaging

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRecorderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMillis: Long = 0L

    fun startRecording(): Result<Unit> = runCatching {
        stopAndRelease()
        val voiceDir = File(context.filesDir, "voice").apply { mkdirs() }
        val file = File(voiceDir, "voice_${UUID.randomUUID()}.m4a")
        outputFile = file

        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        startedAtMillis = System.currentTimeMillis()
    }

    fun stopRecording(): Pair<Uri, Int>? {
        val file = outputFile ?: return null
        val durationSec = ((System.currentTimeMillis() - startedAtMillis) / 1000L).toInt().coerceAtLeast(1)
        return try {
            recorder?.stop()
            stopAndRelease()
            if (file.exists() && file.length() > 0) {
                file.toUri() to durationSec
            } else null
        } catch (_: Exception) {
            stopAndRelease()
            file.delete()
            null
        }
    }

    fun cancelRecording() {
        stopAndRelease()
        outputFile?.delete()
        outputFile = null
    }

    private fun stopAndRelease() {
        runCatching { recorder?.release() }
        recorder = null
    }
}
