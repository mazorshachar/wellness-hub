package com.vitals.app.data.voice

import com.vitals.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Speech to text.
 *
 * Deliberately an interface: this is the one component that sends audio off the
 * device, so it's the one most likely to be swapped — for a different vendor,
 * for a self-hosted model, or for on-device recognition once ML Kit's GenAI
 * speech API leaves alpha and reaches Samsung hardware.
 */
interface Transcriber {
    suspend fun transcribe(audio: ByteArray, filename: String): String?
}

/** OpenAI Whisper. Accepts m4a directly, so no transcoding step is needed. */
class WhisperTranscriber(
    private val apiKey: String = BuildConfig.OPENAI_API_KEY,
    private val client: OkHttpClient = defaultClient,
) : Transcriber {

    override suspend fun transcribe(audio: ByteArray, filename: String): String? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext null

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    filename,
                    audio.toRequestBody("audio/m4a".toMediaType()),
                )
                .addFormDataPart("model", "whisper-1")
                // A hint measurably improves accuracy on food words and brand names.
                .addFormDataPart(
                    "prompt",
                    "A short spoken note about food that was just eaten, " +
                        "including quantities and portion sizes.",
                )
                .build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string().orEmpty())
                json.optString("text").takeIf { it.isNotBlank() }
            }
        }

    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
