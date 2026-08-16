package com.vitals.app.data.food

import com.vitals.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Nutrients(
    val kcal: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val source: String,
)

/**
 * USDA FoodData Central. Public domain data, free API, 1000 requests/hour.
 *
 * Returns null rather than guessing when there's no good match — the caller
 * falls back to the model's own estimate and labels it as such, so a guess is
 * never displayed as if it were a sourced number.
 */
class NutritionLookup(
    private val apiKey: String = BuildConfig.USDA_API_KEY,
    private val client: OkHttpClient = defaultClient,
) {

    suspend fun lookup(searchTerm: String, grams: Double?): Nutrients? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext null

            val url = "https://api.nal.usda.gov/fdc/v1/foods/search".toHttpUrl()
                .newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("query", searchTerm)
                // Foundation and SR Legacy are generic whole foods with reliable
                // per-100g values. Branded entries are noisy and often mislabelled.
                .addQueryParameter("dataType", "Foundation,SR Legacy")
                .addQueryParameter("pageSize", "3")
                .build()

            val request = Request.Builder().url(url).get().build()

            val candidates = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    JSONObject(response.body?.string().orEmpty()).optJSONArray("foods")
                }
            }.getOrNull() ?: return@withContext null

            // FDC search is fuzzy and will happily return a top hit for anything.
            // Taking it blindly meant every food "matched", so the fallback tiers
            // and the ask-the-user path were unreachable in practice.
            val food = (0 until candidates.length())
                .mapNotNull { candidates.optJSONObject(it) }
                .firstOrNull { relevant(searchTerm, it.optString("description")) }
                ?: return@withContext null

            val nutrients = food.optJSONArray("foodNutrients") ?: return@withContext null

            var kcal: Double? = null
            var protein: Double? = null
            var carbs: Double? = null
            var fat: Double? = null

            for (i in 0 until nutrients.length()) {
                val n = nutrients.optJSONObject(i) ?: continue
                val value = n.optDouble("value").takeIf { !it.isNaN() } ?: continue
                // Nutrient numbers are stable identifiers; names vary by dataset.
                when (n.optString("nutrientNumber")) {
                    "208" -> kcal = value       // Energy, kcal
                    "203" -> protein = value    // Protein
                    "205" -> carbs = value      // Carbohydrate, by difference
                    "204" -> fat = value        // Total lipid (fat)
                }
            }

            val energyPer100g = kcal ?: return@withContext null
            // USDA values are per 100 g. With no portion estimate, assume 100 g
            // rather than silently reporting a whole-food-per-100g figure as a serving.
            val scale = (grams ?: 100.0) / 100.0

            Nutrients(
                kcal = energyPer100g * scale,
                proteinG = protein?.times(scale),
                carbsG = carbs?.times(scale),
                fatG = fat?.times(scale),
                source = "USDA",
            )
        }

    /**
     * Requires every meaningful word of the query to appear in the matched food's
     * description. "chicken shawarma" must not silently resolve to "Chicken, raw".
     */
    private fun relevant(searchTerm: String, description: String): Boolean {
        if (description.isBlank()) return false
        val target = description.lowercase()
        val words = searchTerm.lowercase()
            .split(Regex("[^a-z]+"))
            .filter { it.length > 2 && it !in STOP_WORDS }
        return words.isNotEmpty() && words.all { target.contains(it) }
    }

    companion object {
        private val STOP_WORDS = setOf("raw", "cooked", "fresh", "plain", "whole")

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
