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
import java.util.Collections

object GeminiApiWrapper {

    // ─────────────── Public model ────────────────
    data class ChatMessage(val role: String, val text: String)

    /** Thread‑safe single‑process default history.  You *may* ignore it and
     *  provide your own list on each call if you need per‑conversation state
     *  that survives process death (e.g. persist to Room / Preferences). */
    val inMemoryHistory: MutableList<ChatMessage> =
        Collections.synchronizedList(mutableListOf())

    /** Remove all messages from [inMemoryHistory]. */
    fun clearHistory() = inMemoryHistory.clear()

    // ─────────────── Config constants ────────────────
    private const val SPEECH_API_URL = "https://speech.googleapis.com/v1/speech:recognize"
    private const val MODEL_NAME     = "gemini-2.5-flash"   // change if needed
    private const val TEXT_API_URL   =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"

    private const val SAMPLE_RATE_HZ = 16_000
    private const val LANGUAGE_CODE  = "en-US"

    private val API_KEY     = BuildConfig.GEMINI_API_KEY
    private val httpClient  = OkHttpClient()

    // ─────────────────── High‑level helper ───────────────────
    /**
     * Convenience method: transcribes *[wavFile]*, appends the user transcript
     * to *[history]*, sends the full chat to Gemini, appends Gemini's reply
     * back into *[history]*, and finally returns the reply text.
     *
     * If you do **not** want to keep any local state, simply pass a new
     * `mutableListOf()` every time.  If you *do* want state, reuse the same
     * list (e.g. [inMemoryHistory]).
     */
    suspend fun sendWavWithHistory(
        wavFile: File,
        systemPrompt: String? = null,
        history: MutableList<ChatMessage> = inMemoryHistory
    ): String = withContext(Dispatchers.IO) {
        val userTranscript = transcribeSpeech(wavFile)
        history += ChatMessage("user", userTranscript)

        val reply = generateGeminiReply(history, systemPrompt)
        history += ChatMessage("model", reply)
        reply
    }

    // ─────────────────── Speech‑to‑Text ───────────────────
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
                throw RuntimeException("Speech‑to‑Text error ${resp.code}: ${resp.message}")
            }
            val results = JSONObject(resp.body?.string().orEmpty())
                .optJSONArray("results")
                ?: throw RuntimeException("No transcription results.")

            return results.getJSONObject(0)
                .getJSONArray("alternatives")
                .getJSONObject(0)
                .getString("transcript")
        }
    }

    // ─────────────────── Gemini chat ───────────────────
    /** Build JSON for the Gemini *generateContent* endpoint using the full
     * message stream in [history]. */
    private fun buildGeminiChatRequestBody(
        history: List<ChatMessage>,
        systemPrompt: String?
    ): String = JSONObject().apply {
        if (!systemPrompt.isNullOrBlank()) {
            put("system_instruction", JSONObject().apply {
                put("role", "system")
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", systemPrompt))
                })
            })
        }

        put("contents", JSONArray().apply {
            history.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", msg.text))
                    })
                })
            }
        })
    }.toString()

    /**
     * Core network call – takes the entire *[history]* (user + model messages)
     * and optional *[systemPrompt]*, returns Gemini's latest reply text.
     */
    fun generateGeminiReply(
        history: List<ChatMessage>,
        systemPrompt: String? = null
    ): String {
        val url  = TEXT_API_URL.format(MODEL_NAME, API_KEY)
        val body = buildGeminiChatRequestBody(history, systemPrompt)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("Gemini API error ${resp.code}: ${resp.message}")
            }
            val candidates = JSONObject(resp.body?.string().orEmpty())
                .optJSONArray("candidates")
                ?: throw RuntimeException("No candidates returned by Gemini.")

            val parts = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")

            return if (parts.length() > 0) parts.getJSONObject(0).optString("text", "") else ""
        }
    }
}
