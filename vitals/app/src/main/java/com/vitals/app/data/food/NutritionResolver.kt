package com.vitals.app.data.food

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Where a food's numbers came from. Surfaced in the UI so a guess is never
 * mistaken for a measurement.
 */
enum class NutritionSource { USDA, OPEN_FOOD_FACTS, ESTIMATE, NEEDS_INPUT }

/**
 * Resolves a spoken food to nutrients through a ladder, stopping at the first
 * tier that answers:
 *
 *  1. USDA FoodData Central — generic whole foods ("banana", "chicken breast")
 *  2. Open Food Facts — branded and packaged items USDA doesn't carry
 *  3. The model's own estimate — only when it said it was confident
 *  4. Ask the user — anything else, rather than inventing a number
 *
 * Tier 4 is the important one. Silently guessing at an unknown food is how a
 * calorie tracker quietly becomes fiction.
 */
class NutritionResolver(
    private val usda: NutritionLookup = NutritionLookup(),
    private val openFoodFacts: OpenFoodFactsLookup = OpenFoodFactsLookup(),
) {

    suspend fun resolve(food: ParsedFood): Nutrients {
        // Without a portion weight, a per-100g table would be scaled to a
        // fabricated serving and still labelled as sourced data. Ask instead.
        val grams = food.estimatedGrams
            ?: return needsInput(food)

        usda.lookup(food.searchTerm, grams)?.let { return it }

        openFoodFacts.lookup(food.searchTerm, grams)?.let { return it }

        // The model flags its own uncertainty. Trust a confident estimate for a
        // common food; escalate anything else to the user instead of guessing.
        if (food.confidence.equals("high", ignoreCase = true) && food.fallbackKcal > 0) {
            return Nutrients(
                kcal = food.fallbackKcal,
                proteinG = null,
                carbsG = null,
                fatG = null,
                source = NutritionSource.ESTIMATE.name,
            )
        }

        return needsInput(food)
    }

    /** Keeps the model's guess as a seed for the input box, but never counts it. */
    private fun needsInput(food: ParsedFood) = Nutrients(
        kcal = food.fallbackKcal,
        proteinG = null,
        carbsG = null,
        fatG = null,
        source = NutritionSource.NEEDS_INPUT.name,
    )
}

/**
 * Open Food Facts — free, no API key, and strong on branded and non-US products,
 * which is exactly where USDA's generic tables fall short.
 */
class OpenFoodFactsLookup(
    private val client: OkHttpClient = defaultClient,
) {

    suspend fun lookup(searchTerm: String, grams: Double?): Nutrients? =
        withContext(Dispatchers.IO) {
            val url = "https://world.openfoodfacts.org/cgi/search.pl".toHttpUrl()
                .newBuilder()
                .addQueryParameter("search_terms", searchTerm)
                .addQueryParameter("search_simple", "1")
                .addQueryParameter("action", "process")
                .addQueryParameter("json", "1")
                .addQueryParameter("page_size", "1")
                .addQueryParameter(
                    "fields",
                    "product_name,nutriments",
                )
                .build()

            val request = Request.Builder()
                .url(url)
                // Open Food Facts requires an identifying User-Agent.
                .addHeader("User-Agent", "Vitals/1.0 (personal health tracker)")
                .get()
                .build()

            val nutriments = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    JSONObject(response.body?.string().orEmpty())
                        .optJSONArray("products")
                        ?.optJSONObject(0)
                        ?.optJSONObject("nutriments")
                }
            }.getOrNull() ?: return@withContext null

            val per100g = nutriments.optDouble("energy-kcal_100g")
                .takeIf { !it.isNaN() && it > 0 }
                ?: return@withContext null

            val scale = (grams ?: 100.0) / 100.0

            Nutrients(
                kcal = per100g * scale,
                proteinG = nutriments.optDouble("proteins_100g")
                    .takeIf { !it.isNaN() }?.times(scale),
                carbsG = nutriments.optDouble("carbohydrates_100g")
                    .takeIf { !it.isNaN() }?.times(scale),
                fatG = nutriments.optDouble("fat_100g")
                    .takeIf { !it.isNaN() }?.times(scale),
                source = NutritionSource.OPEN_FOOD_FACTS.name,
            )
        }

    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
