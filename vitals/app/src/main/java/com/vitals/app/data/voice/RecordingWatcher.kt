package com.vitals.app.data.voice

import android.content.Context
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vitals.app.VitalsApp
import java.util.concurrent.TimeUnit

/**
 * The hook: the system wakes this app when a new recording appears.
 *
 * `addContentUriTrigger` registers the observer inside system_server, not inside
 * this process — so the app can be fully dead and Android will still start it
 * when MediaStore changes. That's the difference between this and a plain
 * ContentObserver, which dies with the process.
 *
 * Two things about it are counterintuitive and both are load-bearing:
 *
 *  - **It cannot be periodic.** JobScheduler throws on a periodic job with a
 *    content trigger, so the work re-arms itself at the end of every run.
 *  - **It is a wake-up signal, not a list of changes.** The system caps its
 *    change report at 50 URIs, and changes landing between one run finishing
 *    and the next arming are simply lost. So the trigger only says "look now" —
 *    what actually gets processed is decided by re-querying MediaStore, which
 *    is what the pipeline already does.
 */
class NewRecordingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? VitalsApp ?: return Result.success()

        try {
            app.pipeline.run()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // A deliberate cancel should stay cancelled rather than re-arming.
            throw cancellation
        } catch (_: Throwable) {
            // Swallowed deliberately. Returning retry() here would leave the
            // trigger unarmed until the retry ran; re-arming below matters more
            // than this particular sweep, and the pipeline tracks its own
            // per-recording retries anyway.
        }

        // Must be the last thing that happens. Re-arming before or during
        // processing causes the system to stop the job mid-run.
        arm(applicationContext, ExistingWorkPolicy.REPLACE)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "recording-watch"

        /**
         * (Re)registers the trigger.
         *
         * The policy is not cosmetic. From [doWork] it must be REPLACE, because
         * the current worker hasn't finished and KEEP would silently drop the
         * re-arm — the hook would then fire exactly once, ever.
         *
         * From cold start it must be KEEP, because the system starting this
         * process to run a triggered job calls Application.onCreate first, and
         * REPLACE there would cancel the very job it was starting.
         */
        fun arm(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val constraints = Constraints.Builder()
                // Item URIs are descendants of the collection URI, so without
                // this flag a new file never fires anything.
                .addContentUriTrigger(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    /* triggerForDescendants = */ true,
                )
                // Which volume name MediaProvider notifies on isn't guaranteed;
                // registering both costs nothing and closes the gap.
                .addContentUriTrigger(
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    /* triggerForDescendants = */ true,
                )
                // The update delay restarts on each further change, so this
                // batches a burst of writes into one run. Max delay caps how
                // long that batching can defer us.
                .setTriggerContentUpdateDelay(2, TimeUnit.SECONDS)
                .setTriggerContentMaxDelay(30, TimeUnit.SECONDS)
                // Transcription needs the network. Without this the job would
                // wake offline, fail, and spend one of the recording's three
                // retry attempts on an outage that wasn't its fault.
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<NewRecordingWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
