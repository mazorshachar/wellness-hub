package com.vitals.app.data.voice

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant

/**
 * A voice note found in shared storage.
 *
 * Samsung's stock recorder writes to `Recordings/Voice Recorder/`, and notes
 * recorded on a Galaxy Watch are transferred by the Wearable app into
 * `Recordings/Sounds/Watch/`. Both land under `Recordings/`, which is what this
 * scanner keys on — so watch dictation arrives with no watch code at all.
 */
data class VoiceRecording(
    val mediaStoreId: Long,
    val uri: Uri,
    val displayName: String,
    val relativePath: String?,
    val recordedAt: Instant,
    val durationMs: Long,
    val sizeBytes: Long,
)

class RecordingScanner(private val context: Context) {

    /**
     * Voice notes longer than this are assumed to be something else — a meeting,
     * a memo — and are skipped rather than shipped to a transcription API.
     */
    private val maxDurationMs = 120_000L

    /**
     * Returns recordings added after [sinceEpochSeconds], newest first.
     *
     * Paths are never hardcoded. Samsung has moved and renamed these folders
     * across One UI versions, so the query keys on MediaStore's own
     * classification instead. On API 31+ that's the `IS_RECORDING` flag, which
     * the platform sets by matching `/recordings/` anywhere in the path;
     * below that we match RELATIVE_PATH directly.
     */
    fun findNewRecordings(sinceEpochSeconds: Long): List<VoiceRecording> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_TAKEN,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
        )

        // Belt and braces: IS_RECORDING alone can carry stale values for files
        // scanned before an OS upgrade, so the path check backs it up.
        val recordingClause = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "(${MediaStore.Audio.Media.IS_RECORDING} = 1 OR " +
                "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?)"
        } else {
            "(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?)"
        }

        val selection = "$recordingClause AND ${MediaStore.Audio.Media.DATE_ADDED} > ?"
        val args = arrayOf("Recordings/%", sinceEpochSeconds.toString())
        val order = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val results = mutableListOf<VoiceRecording>()

        context.contentResolver.query(collection, projection, selection, args, order)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val takenCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_TAKEN)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (cursor.moveToNext()) {
                    val duration = cursor.getLong(durCol)
                    if (duration > maxDurationMs) continue

                    val id = cursor.getLong(idCol)
                    results += VoiceRecording(
                        mediaStoreId = id,
                        uri = android.content.ContentUris.withAppendedId(collection, id),
                        displayName = cursor.getString(nameCol) ?: "recording",
                        relativePath = cursor.getString(pathCol),
                        // DATE_ADDED is when the file reached the phone, which for a
                        // watch note is after the Wearable app transfers it — that lag
                        // can push a late-evening meal into the next day. DATE_TAKEN is
                        // when it was actually recorded, when the recorder sets it.
                        recordedAt = takenCol.takeIf { it >= 0 }
                            ?.let { cursor.getLong(it) }
                            ?.takeIf { it > 0 }
                            ?.let { Instant.ofEpochMilli(it) }
                            ?: Instant.ofEpochSecond(cursor.getLong(addedCol)),
                        durationMs = duration,
                        sizeBytes = cursor.getLong(sizeCol),
                    )
                }
            }

        return results
    }

    fun readBytes(recording: VoiceRecording): ByteArray? =
        context.contentResolver.openInputStream(recording.uri)?.use { it.readBytes() }

    /**
     * Emits while the app is in the foreground, so a note recorded with the app
     * open appears within a second or two rather than waiting on the system
     * trigger's batching delay.
     *
     * This is the least important of the three mechanisms — it dies with the
     * process — but it's the one the user actually watches happen.
     */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }

        // MediaProvider notifies on the row's own volume name, which for primary
        // shared storage is "external_primary" — an observer on "external" alone
        // may never fire. Registering both mirrors the system trigger.
        listOf(MediaStore.VOLUME_EXTERNAL, MediaStore.VOLUME_EXTERNAL_PRIMARY).forEach { volume ->
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.getContentUri(volume),
                /* notifyForDescendants = */ true,
                observer,
            )
        }

        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
}
