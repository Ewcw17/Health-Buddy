package com.whispercppdemo.api

import android.content.Context
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A simple frontend API for whisper.cpp operations.
 * Wraps model loading, transcription, and cleanup.
 */
class WhisperApi(private val context: Context) {
    private var whisperContext: WhisperContext? = null
    private val modelDir = "models"

    /**
     * Initialize by loading the first model found in the assets/models directory.
     */
    suspend fun initialize(): Unit = withContext(Dispatchers.IO) {
        val models = context.assets.list(modelDir)
        val modelName = models?.firstOrNull()
            ?: throw IllegalStateException("No model file found in assets/$modelDir")
        whisperContext = WhisperContext.createContextFromAsset(
            context.assets,
            "$modelDir/$modelName"
        )
    }

    /**
     * Transcribe a WAV audio file. Returns the recognized text.
     */
    suspend fun transcribe(file: File): String = withContext(Dispatchers.IO) {
        val data = decodeWaveFile(file)
        val ctx = whisperContext
            ?: throw IllegalStateException("WhisperApi is not initialized")
        ctx.transcribeData(data) ?: ""
    }

    /**
     * Release native resources.
     */
    suspend fun release() {
        whisperContext?.release()
        whisperContext = null
    }


    // From whisper.cpp's documentation https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android
    // https://github.com/ggml-org/whisper.cpp?tab=MIT-1-ov-file#readme
    fun decodeWaveFile(file: File): FloatArray {
        val baos = ByteArrayOutputStream()
        file.inputStream().use { it.copyTo(baos) }
        val buffer = ByteBuffer.wrap(baos.toByteArray())
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val channel = buffer.getShort(22).toInt()
        buffer.position(44)
        val shortBuffer = buffer.asShortBuffer()
        val shortArray = ShortArray(shortBuffer.limit())
        shortBuffer.get(shortArray)
        return FloatArray(shortArray.size / channel) { index ->
            when (channel) {
                1 -> (shortArray[index] / 32767.0f).coerceIn(-1f..1f)
                else -> ((shortArray[2*index] + shortArray[2*index + 1])/ 32767.0f / 2.0f).coerceIn(-1f..1f)
            }
        }
    }
}
