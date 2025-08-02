package com.terrabull.healthbuddy.api

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import com.terrabull.healthbuddy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

/**
 * GoogleTtsPlayer
 *
 * Fetches speech audio from Google Cloud Text-to-Speech and plays it once.
 * Usage (inside a coroutine scope on the UI layer):
 *
 *     GoogleTtsPlayer.speak("Hello there!", context)
 *
 * The function throws on network / API errors so you can surface them to the user.
 */
object GoogleTtsPlayer {

    // ───────────── Config ─────────────
    private const val ENDPOINT = "https://texttospeech.googleapis.com/v1/text:synthesize?key=%s"
    private const val LANGUAGE_CODE = "en-US"
    private const val VOICE_NAME    = "en-US-Chirp-HD-F"
    private const val AUDIO_ENCODING = "MP3"
    private val API_KEY   = BuildConfig.GEMINI_API_KEY
    private val http      = OkHttpClient()
    // ──────────────────────────────────

    /**
     * Synthesise [text] and play it through the default audio output.
     * Runs network work on Dispatchers.IO and playback on the Main thread.
     */
    suspend fun speak(text: String, context: Context) = withContext(Dispatchers.IO) {
        // 1) Call Google Cloud TTS
        val body = JSONObject().apply {
            put("input", JSONObject().put("text", text))
            put("voice", JSONObject().apply {
                put("languageCode", LANGUAGE_CODE)
                put("name", VOICE_NAME)
            })
            put("audioConfig", JSONObject().apply {
                put("audioEncoding", AUDIO_ENCODING)
            })
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val req = Request.Builder()
            .url(ENDPOINT.format(API_KEY))
            .post(body)
            .build()

        val pcmBytes = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("TTS error ${resp.code}: ${resp.message}")
            }
            val b64 = JSONObject(resp.body?.string().orEmpty())
                .getString("audioContent")
            Base64.decode(b64, Base64.DEFAULT)
        }

        // 2) Write to a temp MP3 because MediaPlayer needs a file/URL
        val tmp = File.createTempFile("tts_", ".mp3", context.cacheDir).apply {
            writeBytes(pcmBytes)
            deleteOnExit()
        }

        // 3) Play it back on the main thread
        withContext(Dispatchers.Main) {
            MediaPlayer().apply {
                setDataSource(tmp.path)
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    release()
                    tmp.delete()
                }
                prepareAsync()   // async = non-blocking UI
            }
        }
    }
}
