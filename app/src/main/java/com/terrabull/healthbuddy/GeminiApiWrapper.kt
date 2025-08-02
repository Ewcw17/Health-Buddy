package com.terrabull.healthbuddy.api

import android.util.Base64
import com.terrabull.healthbuddy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * GeminiApiWrapper
 *
 * Handles two things:
 * 1. Speech‑to‑Text transcription of RIFF‑WAV via Google Speech‑to‑Text.
 * 2. Text generation via Gemini’s *generateContent* API.
 *
 * The body for generateContent now follows the official v1beta schema:
 * {
 *   "contents":[{"role":"user","parts":[{"text":"…"}]}]
 * }
 */
object GeminiApiWrapper {

    // ───────── Configuration ─────────
    private const val SPEECH_API_URL = "https://speech.googleapis.com/v1/speech:recognize"
    private const val MODEL_NAME     = "gemini-2.5-flash"           // change as needed
    private const val TEXT_API_URL   =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"

    private const val SAMPLE_RATE_HZ = 16_000
    private const val LANGUAGE_CODE  = "en-US"
    // ──────────────────────────────────

    private const val API_KEY  = BuildConfig.GEMINI_API_KEY
    private val httpClient     = OkHttpClient()

    /**
     * Transcribe [wavFile] and feed the text into Gemini, returning the model’s reply.
     */
    suspend fun sendWavForResponse(wavFile: File): String = withContext(Dispatchers.IO) {
        val transcript = transcribeSpeech(wavFile)
        generateGeminiReply(transcript)
    }

    // ---------- Speech‑to‑Text ----------
    private fun buildSttRequestBody(audioBase64: String): String =
        JSONObject().apply {
            put("config", JSONObject().apply {
                put("encoding", "LINEAR16")
                put("sampleRateHertz", SAMPLE_RATE_HZ)
                put("languageCode", LANGUAGE_CODE)
            })
            put("audio", JSONObject().put("content", audioBase64))
        }.toString()

    private fun transcribeSpeech(wavFile: File): String {
        val audioBase64 = Base64.encodeToString(wavFile.readBytes(), Base64.NO_WRAP)
        val sttRequest  = Request.Builder()
            .url("$SPEECH_API_URL?key=$API_KEY")
            .post(buildSttRequestBody(audioBase64)
                .toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(sttRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Speech‑to‑Text error ${response.code}: ${response.message}")
            }
            val resJson = JSONObject(response.body?.string().orEmpty())
            val results = resJson.optJSONArray("results")
                ?: throw RuntimeException("No transcription results.")
            return results.getJSONObject(0)
                .getJSONArray("alternatives")
                .getJSONObject(0)
                .getString("transcript")
        }
    }

    // ------------- Gemini -------------
    private fun buildGeminiRequestBody(prompt: String): String =
        JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
        }.toString()

    private fun generateGeminiReply(prompt: String): String {
        val url = TEXT_API_URL.format(MODEL_NAME, API_KEY)
        val requestBody = buildGeminiRequestBody(prompt)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val genRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        httpClient.newCall(genRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Gemini API error ${response.code}: ${response.message}")
            }
            val resJson    = JSONObject(response.body?.string().orEmpty())
            val candidates = resJson.optJSONArray("candidates")
                ?: throw RuntimeException("No candidates returned by Gemini.")
            val parts = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
            return if (parts.length() > 0) parts.getJSONObject(0).optString("text", "") else ""
        }
    }
}
