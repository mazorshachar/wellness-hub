package com.vitals.app.data.food

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** One food item, parsed out of one spoken note. "Two eggs and toast" is two rows. */
@Entity(tableName = "food_entries")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Which voice note this came from — lets us undo a whole note at once. */
    val recordingId: Long,
    val loggedAtEpochSecond: Long,
    /** The raw transcript, kept so a wrong parse is always traceable. */
    val transcript: String,
    val foodName: String,
    val quantityText: String,
    val grams: Double?,
    val kcal: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    /** See NutritionSource — surfaced in the UI so estimates aren't mistaken for data. */
    val source: String,
    val isDrink: Boolean = false,
    /** No database match and no confident estimate: the app asks rather than invents. */
    val needsReview: Boolean = false,
)

/** Marks a recording as seen, so a note is never transcribed (or billed) twice. */
@Entity(tableName = "processed_recordings")
data class ProcessedRecording(
    @PrimaryKey val mediaStoreId: Long,
    val processedAtEpochSecond: Long,
    /** LOGGED, NOT_FOOD, or FAILED — useful when a note mysteriously didn't land. */
    val outcome: String,
    val detail: String? = null,
    /** FAILED notes are retried a few times; a network blip shouldn't lose a meal. */
    val attempts: Int = 1,
)

@Dao
interface FoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<FoodEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markProcessed(record: ProcessedRecording)

    @Query("SELECT * FROM processed_recordings WHERE mediaStoreId = :id")
    suspend fun processedRecord(id: Long): ProcessedRecording?

    @Query(
        "SELECT * FROM food_entries WHERE loggedAtEpochSecond >= :startOfDay " +
            "ORDER BY loggedAtEpochSecond DESC"
    )
    fun entriesSince(startOfDay: Long): Flow<List<FoodEntry>>

    @Query("DELETE FROM food_entries WHERE recordingId = :recordingId")
    suspend fun deleteByRecording(recordingId: Long)

    /**
     * Delete-then-insert as one transaction. Separately they interleave: two
     * sweeps of the same recording could delete, delete, insert, insert and
     * leave the meal counted twice.
     */
    @Transaction
    suspend fun replaceEntriesForRecording(recordingId: Long, entries: List<FoodEntry>) {
        deleteByRecording(recordingId)
        insertEntries(entries)
    }

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    /** The user supplying a number the databases didn't have. */
    @Query(
        "UPDATE food_entries SET kcal = :kcal, source = 'USER', needsReview = 0 " +
            "WHERE id = :id"
    )
    suspend fun confirmEntry(id: Long, kcal: Double)
}

@Database(
    entities = [FoodEntry::class, ProcessedRecording::class],
    version = 1,
    exportSchema = false,
)
abstract class FoodDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
}
