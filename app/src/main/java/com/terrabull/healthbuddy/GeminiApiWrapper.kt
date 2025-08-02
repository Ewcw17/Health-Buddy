package com.terrabull.healthbuddy.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import com.terrabull.healthbuddy.BuildConfig

/**
 * A simple wrapper for sending WAV audio files to the Gemini API and receiving a text response.
 */
object GeminiApiWrapper {
    // Replace with the actual Gemini API endpoint
    private const val GEMINI_API_URL = "https://api.gemini.google.com/v1/generateContent"

    private const val API_KEY = BuildConfig.GEMINI_API_KEY

    private val httpClient = OkHttpClient()

    /**
     * Sends a .wav audio file to the Gemini API and returns the transcribed/generated text.
     * @param wavFile The RIFF WAV file to send
     * @return The API's text response
     * @throws IOException on network or parsing errors
     */
    suspend fun sendWavForResponse(wavFile: File): String =
        withContext(Dispatchers.IO) {
            // Prepare the WAV file part
            val wavBody = wavFile
                .asRequestBody("audio/wav".toMediaType())

            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    name = "audio",
                    filename = wavFile.name,
                    body = wavBody
                )
                .build()

            // Build HTTP request
            val request = Request.Builder()
                .url(GEMINI_API_URL)
                .addHeader("Authorization", "Bearer $API_KEY")
                .post(multipart)
                .build()

            // Execute and parse
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RuntimeException("Gemini API error: ${response.code} ${response.message}")
                }
                val bodyString = response.body?.string().orEmpty()
                // Assuming response JSON has a `text` field
                JSONObject(bodyString).optString("text", "")
            }
        }
}
