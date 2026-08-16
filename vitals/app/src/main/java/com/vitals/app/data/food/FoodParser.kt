package com.vitals.app.data.food

import com.vitals.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One food item as understood from speech, before nutrient lookup. */
data class ParsedFood(
    val name: String,
    val quantityText: String,
    val estimatedGrams: Double?,
    /** A clean term to search the USDA database with — "banana", not "a banana I ate". */
    val searchTerm: String,
    /** The model's own calorie guess, used only when the databases have no match. */
    val fallbackKcal: Double,
    /** high / medium / low. Only a confident guess is accepted without asking the user. */
    val confidence: String,
    /** True for drinks, so "I drank a coke" reads correctly in the log. */
    val isDrink: Boolean,
)

data class ParsedNote(
    val isFoodLog: Boolean,
    val items: List<ParsedFood>,
)

/**
 * Turns a loose spoken sentence into structured food items.
 *
 * Uses a forced tool call rather than "reply with JSON", so the response is
 * schema-validated by the API instead of parsed hopefully.
 */
class FoodParser(
    private val apiKey: String = BuildConfig.ANTHROPIC_API_KEY,
    private val client: OkHttpClient = defaultClient,
) {

    suspend fun parse(transcript: String): ParsedNote? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || transcript.isBlank()) return@withContext null

        val schema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put(
                        "isFoodLog",
                        JSONObject()
                            .put("type", "boolean")
                            .put(
                                "description",
                                "True only if the note describes food or drink the " +
                                    "speaker consumed. False for reminders, shopping " +
                                    "lists, intentions about future meals, or anything else.",
                            ),
                    )
                    .put(
                        "items",
                        JSONObject()
                            .put("type", "array")
                            .put(
                                "items",
                                JSONObject()
                                    .put("type", "object")
                                    .put(
                                        "properties",
                                        JSONObject()
                                            .put("name", strField("Human-readable food name, e.g. 'Banana'"))
                                            .put("quantityText", strField("Quantity as spoken, e.g. 'one medium'"))
                                            .put("searchTerm", strField("Bare food term for a nutrition database lookup, e.g. 'banana raw'"))
                                            .put(
                                                "estimatedGrams",
                                                JSONObject()
                                                    .put("type", "number")
                                                    .put("description", "Best estimate of the portion weight in grams"),
                                            )
                                            .put(
                                                "fallbackKcal",
                                                JSONObject()
                                                    .put("type", "number")
                                                    .put("description", "Your own calorie estimate for this portion"),
                                            )
                                            .put(
                                                "confidence",
                                                JSONObject()
                                                    .put("type", "string")
                                                    .put("enum", JSONArray(listOf("high", "medium", "low")))
                                                    .put(
                                                        "description",
                                                        "How confident you are in fallbackKcal. Use 'high' only " +
                                                            "for common foods with well-known calorie counts. Use " +
                                                            "'low' for homemade dishes, restaurant meals, regional " +
                                                            "foods, or anything where portion size is guesswork.",
                                                    ),
                                            )
                                            .put(
                                                "isDrink",
                                                JSONObject()
                                                    .put("type", "boolean")
                                                    .put("description", "True if this was drunk rather than eaten"),
                                            ),
                                    )
                                    .put(
                                        "required",
                                        JSONArray(
                                            listOf(
                                                "name", "quantityText", "searchTerm",
                                                "estimatedGrams", "fallbackKcal", "confidence",
                                            )
                                        ),
                                    ),
                            ),
                    ),
            )
            .put("required", JSONArray(listOf("isFoodLog", "items")))

        val tool = JSONObject()
            .put("name", "log_food")
            .put("description", "Record the food and drink described in a spoken note.")
            .put("input_schema", schema)

        val payload = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 1024)
            .put("tools", JSONArray().put(tool))
            .put("tool_choice", JSONObject().put("type", "tool").put("name", "log_food"))
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            "Extract the food and drink from this spoken note. Split multiple " +
                                "items into separate entries. If the speaker did not actually " +
                                "eat or drink something, set isFoodLog to false and return no " +
                                "items.\n\nBe honest about confidence — a wrong number the user " +
                                "trusts is worse than being asked to fill one in.\n\n" +
                                "Note: \"$transcript\"",
                        ),
                ),
            )

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val root = JSONObject(response.body?.string().orEmpty())
            val content = root.optJSONArray("content") ?: return@withContext null

            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") != "tool_use") continue
                val input = block.optJSONObject("input") ?: continue
                return@withContext readNote(input)
            }
            null
        }
    }

    private fun readNote(input: JSONObject): ParsedNote {
        val isFoodLog = input.optBoolean("isFoodLog", false)
        val array = input.optJSONArray("items") ?: JSONArray()

        val items = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                add(
                    ParsedFood(
                        name = name,
                        quantityText = item.optString("quantityText", ""),
                        estimatedGrams = item.optDouble("estimatedGrams")
                            .takeIf { !it.isNaN() && it > 0 },
                        searchTerm = item.optString("searchTerm").ifBlank { name },
                        fallbackKcal = item.optDouble("fallbackKcal", 0.0)
                            .takeIf { !it.isNaN() } ?: 0.0,
                        confidence = item.optString("confidence", "low"),
                        isDrink = item.optBoolean("isDrink", false),
                    )
                )
            }
        }

        return ParsedNote(isFoodLog = isFoodLog, items = items)
    }

    private fun strField(description: String) = JSONObject()
        .put("type", "string")
        .put("description", description)

    companion object {
        /** Cheapest model that handles this reliably. Check the model list before changing. */
        const val MODEL = "claude-haiku-4-5"

        private val JSON = "application/json".toMediaType()

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
