package com.vitals.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Thin wrapper over the Health Connect client.
 *
 * Everything here is read-only. The app never writes back to Health Connect,
 * and never sends health data off the device.
 */
class HealthConnectManager(private val context: Context) {

    /** Health Connect ships inside Android 14+; older versions install it from Play. */
    private val providerPackage = "com.google.android.apps.healthdata"

    companion object {
        /** Readable history without the READ_HEALTH_DATA_HISTORY permission. */
        const val HISTORY_DAYS = 30
    }

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
    )

    fun availability(): AvailabilityState =
        when (HealthConnectClient.getSdkStatus(context, providerPackage)) {
            HealthConnectClient.SDK_AVAILABLE -> AvailabilityState.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                AvailabilityState.NeedsProviderUpdate
            else -> AvailabilityState.NotSupported
        }

    // Built once. A `get()` here would construct a fresh client on every one of
    // the ~40 reads a single refresh makes.
    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context, providerPackage)
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()

    suspend fun hasAnyPermission(): Boolean =
        grantedPermissions().any { it in permissions }

    /** Sends the user to the Play listing so they can install/update Health Connect. */
    fun openProviderInstall() {
        val uri = Uri.parse(
            "market://details?id=$providerPackage&url=healthconnect%3A%2F%2Fonboarding"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Opens the Health Connect settings screen, where the user manages grants.
     *
     * Note this constant is computed, not a fixed string: on Android 14+ it
     * resolves to the platform settings action, and to the androidx action
     * below that. Hardcoding the old literal silently no-ops on Android 14+.
     */
    fun openHealthConnectSettings() {
        val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    // ---------------------------------------------------------------- reading

    /**
     * Pulls everything the dashboard shows.
     *
     * Each read is independent: if the user granted steps but denied blood
     * pressure, the steps card still fills in rather than the whole screen
     * failing. Denied reads simply come back null.
     */
    suspend fun loadSnapshot(zone: ZoneId = ZoneId.systemDefault()): HealthSnapshot {
        val today = LocalDate.now(zone)
        val days = (0L..6L).map { today.minusDays(it) }.reversed()

        val perDay = days.map { date -> date to summarizeDay(date) }
        val todaySummary = perDay.lastOrNull()?.second ?: DaySummary()

        // Android 15+ caps reads at 30 days unless READ_HEALTH_DATA_HISTORY is
        // granted, which this app doesn't request. Asking for 90 would silently
        // return 30 and mislabel it.
        val since = today.minusDays(HISTORY_DAYS - 1L).atStartOfDay(zone).toInstant()
        val body = readBodyHistory(since)
        val pressure = readBloodPressure(since)

        return HealthSnapshot(
            today = todaySummary,
            lastSevenDays = perDay,
            latestBody = body.firstOrNull(),
            previousBody = body.getOrNull(1),
            bodyHistory = body,
            latestBloodPressure = pressure.firstOrNull(),
            bloodPressureHistory = pressure,
        )
    }

    /** LocalDateTime bounds mean Health Connect resolves the day in the device's zone. */
    private suspend fun summarizeDay(date: LocalDate): DaySummary {
        val range = TimeRangeFilter.between(
            date.atStartOfDay(),
            date.plusDays(1).atStartOfDay(),
        )

        // Aggregates are requested in small groups so one denied permission
        // doesn't wipe out the unrelated metrics in the same call.
        val steps = aggregateOrNull(setOf(StepsRecord.COUNT_TOTAL), range)
            ?.get(StepsRecord.COUNT_TOTAL)

        val total = aggregateOrNull(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), range)
            ?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories

        val active = aggregateOrNull(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range)
            ?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories

        val heart = aggregateOrNull(
            setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX),
            range,
        )

        val sleep = aggregateOrNull(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), range)
            ?.get(SleepSessionRecord.SLEEP_DURATION_TOTAL)

        val exercise = aggregateOrNull(setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL), range)
            ?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)

        return DaySummary(
            steps = steps,
            totalCalories = total,
            activeCalories = active,
            avgHeartRate = heart?.get(HeartRateRecord.BPM_AVG),
            minHeartRate = heart?.get(HeartRateRecord.BPM_MIN),
            maxHeartRate = heart?.get(HeartRateRecord.BPM_MAX),
            sleep = sleep,
            exercise = exercise,
        )
    }

    /**
     * Runs a read and returns null if it fails — usually because the user denied
     * that particular permission. CancellationException is deliberately rethrown
     * so coroutine cancellation still propagates.
     */
    private inline fun <T> tolerate(block: () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private suspend fun aggregateOrNull(
        metrics: Set<AggregateMetric<*>>,
        range: TimeRangeFilter,
    ) = tolerate {
        client.aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = range))
    }

    /**
     * Weight and body fat arrive as separate record types, often written at the
     * same moment by a smart scale. They're merged by day so one reading shows
     * both numbers instead of two half-empty rows.
     */
    private suspend fun readBodyHistory(since: Instant): List<BodyReading> {
        val range = TimeRangeFilter.between(since, Instant.now())

        val weights = tolerate {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = range,
                    ascendingOrder = false,
                )
            ).records
        }.orEmpty()

        val fats = tolerate {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = BodyFatRecord::class,
                    timeRangeFilter = range,
                    ascendingOrder = false,
                )
            ).records
        }.orEmpty()

        val zone = ZoneId.systemDefault()
        val byDay = sortedMapOf<LocalDate, BodyReading>(compareByDescending { it })

        weights.forEach { record ->
            val day = LocalDateTime.ofInstant(record.time, zone).toLocalDate()
            val existing = byDay[day]
            byDay[day] = (existing ?: BodyReading(time = record.time)).copy(
                time = maxOf(existing?.time ?: record.time, record.time),
                // Records arrive newest-first, so the first one seen for a day
                // wins. Overwriting here would keep the day's EARLIEST weigh-in.
                weightKg = existing?.weightKg ?: record.weight.inKilograms,
                source = existing?.source ?: record.metadata.dataOrigin.packageName,
            )
        }

        fats.forEach { record ->
            val day = LocalDateTime.ofInstant(record.time, zone).toLocalDate()
            val existing = byDay[day]
            byDay[day] = (existing ?: BodyReading(time = record.time)).copy(
                time = maxOf(existing?.time ?: record.time, record.time),
                bodyFatPercent = existing?.bodyFatPercent ?: record.percentage.value,
                source = existing?.source ?: record.metadata.dataOrigin.packageName,
            )
        }

        return byDay.values.toList()
    }

    private suspend fun readBloodPressure(since: Instant): List<BloodPressureReading> {
        val range = TimeRangeFilter.between(since, Instant.now())
        return tolerate {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = BloodPressureRecord::class,
                    timeRangeFilter = range,
                    ascendingOrder = false,
                )
            ).records.map { record ->
                BloodPressureReading(
                    time = record.time,
                    systolic = record.systolic.inMillimetersOfMercury,
                    diastolic = record.diastolic.inMillimetersOfMercury,
                    source = record.metadata.dataOrigin.packageName,
                )
            }
        }.orEmpty()
    }
}
