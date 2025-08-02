package com.example.recorder   // same package as before

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AudioRecorder(private val context: Context) {

    sealed class State {
        object Idle : State()
        object Recording : State()
        data class Finished(val file: File) : State()
    }

    var state: State = State.Idle
        private set

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    /**
     * Begins recording to an Opus file in the app cache directory.
     *
     * @return the file that *will* hold the audio once you call [stop].
     */
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun start(): File {
        check(state == State.Idle) { "start() called, but recorder is already in use." }

        // Target filename:  rec_20250802_194233.opus
        outputFile = File(
            context.cacheDir,
            "rec_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(Date()) + ".opus"
        )

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)

            // -- Opus in Ogg --
            setOutputFormat(MediaRecorder.OutputFormat.OGG)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)

            // Recommended settings for Opus
            setAudioSamplingRate(48_000)      // Opus operates internally at 48 kHz
            setAudioEncodingBitRate(64_000)   // change if you want higher fidelity

            setOutputFile(outputFile!!.absolutePath)
            prepare()
            start()
        }

        state = State.Recording
        return outputFile!!
    }

    /** Stops recording and returns the finished file. */
    fun stop(): File {
        check(state == State.Recording) { "stop() called while not recording." }
        recorder?.apply { stop() }
        state = State.Finished(outputFile!!)
        return outputFile!!
    }

    /** Abandons any recording and deletes the partial file. */
    fun cancel() {
        recorder?.runCatching { stop() }
        outputFile?.delete()
        state = State.Idle
    }

    /** Must be called from `onDestroy` or when you’re done. */
    fun release() {
        recorder?.release()
        recorder = null
    }
}
