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
 * 2. Text generation via Gemini’s *generateContent* REST endpoint.
 *
 * ---
 * **2025‑08‑02**
 * » Fixed 400 "Bad Request" when using a system prompt.
 *   The Gemini REST API expects *system instructions* in a **top‑level**
 *   `system_instruction` object, **not** as another element in `contents`.
 *   This rewrite moves the prompt accordingly.
 *
 * Usage:
 * ```kotlin
 * val reply = GeminiApiWrapper.sendWavForResponse(
 *     wavFile,
 *     systemPrompt = "You are a friendly medical assistant."
 * )
 * ```
 */
object GeminiApiWrapper {

    // ───────── Configuration ─────────
    private const val SPEECH_API_URL = "https://speech.googleapis.com/v1/speech:recognize"
    private const val MODEL_NAME     = "gemini-2.5-flash"       // change if needed
    private const val TEXT_API_URL   =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"

    private const val SAMPLE_RATE_HZ = 16_000
    private const val LANGUAGE_CODE  = "en-US"
    // ──────────────────────────────────

    private val API_KEY     = BuildConfig.GEMINI_API_KEY
    private val httpClient  = OkHttpClient()

    /**
     * High‑level helper: transcribe *[wavFile]* then get a Gemini reply.  If
     * *[systemPrompt]* is provided it will be sent as `system_instruction`.
     */
    suspend fun sendWavForResponse(
        wavFile: File,
        systemPrompt: String? = null
    ): String = withContext(Dispatchers.IO) {
        val transcript = transcribeSpeech(wavFile)
        generateGeminiReply(transcript, systemPrompt)
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
        val requestBody = buildSttRequestBody(audioBase64)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$SPEECH_API_URL?key=$API_KEY")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("Speech-to-Text error ${resp.code}: ${resp.message}")
            }
            val resJson = JSONObject(resp.body?.string().orEmpty())
            val results = resJson.optJSONArray("results")
                ?: throw RuntimeException("No transcription results.")
            return results.getJSONObject(0)
                .getJSONArray("alternatives")
                .getJSONObject(0)
                .getString("transcript")
        }
    }

    // ------------- Gemini -------------
    private fun buildGeminiRequestBody(userPrompt: String, systemPrompt: String?): String =
        JSONObject().apply {
            // Optional system instruction (top‑level)
            if (!systemPrompt.isNullOrBlank()) {
                put("system_instruction", JSONObject().apply {
                    put("role", "system") // role is ignored by API but kept for clarity
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemPrompt))
                    })
                })
            }

            // Required user contents
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", userPrompt))
                    })
                })
            })
        }.toString()

    /**
     * Low‑level text generation call.  Supply the *[userPrompt]* and optional
     * *[systemPrompt]*.  Throws RuntimeException on non‑2xx HTTP codes or empty
     * response.
     */
    fun generateGeminiReply(userPrompt: String, systemPrompt: String? = null): String {
        val url = TEXT_API_URL.format(MODEL_NAME, API_KEY)
        val body = buildGeminiRequestBody(userPrompt, systemPrompt)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("Gemini API error ${resp.code}: ${resp.message}")
            }
            val resJson    = JSONObject(resp.body?.string().orEmpty())
            val candidates = resJson.optJSONArray("candidates")
                ?: throw RuntimeException("No candidates returned by Gemini.")
            val parts = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
            return if (parts.length() > 0) parts.getJSONObject(0).optString("text", "") else ""
        }
    }
}