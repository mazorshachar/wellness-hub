package com.vitals.app.data.voice

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.vitals.app.data.food.FoodDao
import com.vitals.app.data.food.FoodEntry
import com.vitals.app.data.food.FoodParser
import com.vitals.app.data.food.NutritionResolver
import com.vitals.app.data.food.NutritionSource
import com.vitals.app.data.food.ProcessedRecording
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Voice note in, food entries out.
 *
 * Runs: find new recordings → transcribe → parse → look up nutrients → store.
 * Every recording is marked processed exactly once, including failures, so a
 * broken note is never retried forever and never billed twice.
 */
class VoiceLogPipeline(
    private val context: Context,
    private val scanner: RecordingScanner,
    private val transcriber: Transcriber,
    private val parser: FoodParser,
    private val nutrition: NutritionResolver,
    private val dao: FoodDao,
) {

    data class Result(val scanned: Int, val logged: Int, val skipped: Int, val failed: Int)

    fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * One sweep at a time. The content trigger and the in-app observer both fire
     * within seconds of the same new file, and the dedupe check is a read of the
     * processed table followed by a network round trip — so without this, both
     * sweeps see "not processed", both pay for transcription, and the meal is
     * logged twice.
     */
    private val runLock = Mutex()

    suspend fun run(): Result = runLock.withLock { sweep() }

    private suspend fun sweep(): Result = withContext(Dispatchers.IO) {
        if (!hasAudioPermission()) return@withContext Result(0, 0, 0, 0)

        // Always sweep a fixed 24-hour window rather than advancing a high-water
        // mark. A mark would step past a note that failed on its first attempt,
        // making the retry logic below unreachable. The window is a handful of
        // rows, and the processed table is what actually prevents duplicates.
        val since = Instant.now().epochSecond - LOOKBACK_SECONDS
        val recordings = scanner.findNewRecordings(since)

        var logged = 0
        var skipped = 0
        var failed = 0

        for (recording in recordings) {
            val previous = dao.processedRecord(recording.mediaStoreId)

            // Settled results are final. Failures get a few more attempts, so a
            // dropped connection doesn't silently lose a logged meal — but a
            // permanently unreadable file stops burning API calls.
            if (previous != null &&
                (previous.outcome != "FAILED" || previous.attempts >= MAX_ATTEMPTS)
            ) {
                continue
            }

            // One bad recording must not abort the sweep. Without this, a single
            // network blip skips every remaining note AND never increments
            // attempts, so the retry budget below could never be spent.
            val (outcome, detail) = try {
                processOne(recording)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                "FAILED" to (t.message ?: t::class.java.simpleName)
            }
            when (outcome) {
                "LOGGED" -> logged++
                "NOT_FOOD" -> skipped++
                else -> failed++
            }

            dao.markProcessed(
                ProcessedRecording(
                    mediaStoreId = recording.mediaStoreId,
                    processedAtEpochSecond = Instant.now().epochSecond,
                    outcome = outcome,
                    detail = detail,
                    attempts = (previous?.attempts ?: 0) + 1,
                )
            )
        }

        Result(recordings.size, logged, skipped, failed)
    }

    /** Returns outcome to record, plus a short detail for debugging. */
    private suspend fun processOne(recording: VoiceRecording): Pair<String, String?> {
        val audio = scanner.readBytes(recording)
            ?: return "FAILED" to "could not read audio"

        val transcript = transcriber.transcribe(audio, recording.displayName)
            ?: return "FAILED" to "transcription returned nothing"

        val note = parser.parse(transcript)
            ?: return "FAILED" to "parse failed for: $transcript"

        if (!note.isFoodLog || note.items.isEmpty()) {
            return "NOT_FOOD" to transcript
        }

        val entries = note.items.map { item ->
            val resolved = nutrition.resolve(item)

            FoodEntry(
                recordingId = recording.mediaStoreId,
                loggedAtEpochSecond = recording.recordedAt.epochSecond,
                transcript = transcript,
                foodName = item.name,
                quantityText = item.quantityText,
                grams = item.estimatedGrams,
                kcal = resolved.kcal,
                proteinG = resolved.proteinG,
                carbsG = resolved.carbsG,
                fatG = resolved.fatG,
                source = resolved.source,
                isDrink = item.isDrink,
                needsReview = resolved.source == NutritionSource.NEEDS_INPUT.name,
            )
        }

        // Idempotent: if a previous attempt inserted rows but died before marking
        // the recording processed, this replaces them rather than doubling the meal.
        dao.replaceEntriesForRecording(recording.mediaStoreId, entries)
        return "LOGGED" to transcript
    }

    private companion object {
        /**
         * How far back each sweep looks. Generous, because the watch-to-phone
         * transfer can lag well behind when the note was actually spoken.
         */
        const val LOOKBACK_SECONDS = 86_400L

        /** Retries before a note is written off as unprocessable. */
        const val MAX_ATTEMPTS = 3
    }
}
