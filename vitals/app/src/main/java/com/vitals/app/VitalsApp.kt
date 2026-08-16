package com.vitals.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vitals.app.data.food.FoodDatabase
import com.vitals.app.data.food.FoodParser
import com.vitals.app.data.food.NutritionResolver
import com.vitals.app.data.voice.NewRecordingWorker
import com.vitals.app.data.voice.RecordingScanner
import com.vitals.app.data.voice.VoiceLogPipeline
import com.vitals.app.data.voice.WhisperTranscriber
import java.util.concurrent.TimeUnit

class VitalsApp : Application() {

    val database: FoodDatabase by lazy {
        Room.databaseBuilder(this, FoodDatabase::class.java, "vitals.db").build()
    }

    val scanner: RecordingScanner by lazy { RecordingScanner(this) }

    val pipeline: VoiceLogPipeline by lazy {
        VoiceLogPipeline(
            context = this,
            scanner = scanner,
            transcriber = WhisperTranscriber(),
            parser = FoodParser(),
            nutrition = NutritionResolver(),
            dao = database.foodDao(),
        )
    }

    override fun onCreate() {
        super.onCreate()

        // Three layers, deliberately overlapping, because none of them is
        // reliable alone:
        //
        //  1. NewRecordingWorker — the hook. System-side MediaStore trigger,
        //     fires within seconds while the device is awake, works with the
        //     app dead. Can be dropped in Doze or the restricted standby bucket.
        //  2. VoiceScanWorker — a 15-minute safety net that catches anything
        //     the trigger missed while the phone was idle in a pocket.
        //  3. A ContentObserver in the ViewModel, and a scan on every resume,
        //     for instant feedback while the app is actually open.
        //
        // The hook is gated on the permission: without it every sweep is a
        // guaranteed no-op, and the trigger fires for ANY app's audio file, so a
        // WhatsApp voice note would wake this process to do nothing.
        // MainActivity arms it the moment the user grants access.
        if (pipeline.hasAudioPermission()) NewRecordingWorker.arm(this)
        scheduleVoiceScan()
    }

    /**
     * The floor for periodic work is 15 minutes. This exists purely as a
     * backstop for the content trigger — Doze defers jobs to maintenance
     * windows, so a note recorded overnight may not be processed until morning
     * regardless of which mechanism notices it.
     */
    private fun scheduleVoiceScan() {
        val request = PeriodicWorkRequestBuilder<VoiceScanWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "voice-scan",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

class VoiceScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? VitalsApp ?: return Result.success()
        return try {
            app.pipeline.run()
            Result.success()
        } catch (t: Throwable) {
            // Network blips and API hiccups are expected; retry on the next tick
            // rather than dropping the note.
            Result.retry()
        }
    }
}
