package com.vitals.app.data

import java.time.Duration
import java.time.Instant

/** A single day's aggregated numbers. */
data class DaySummary(
    val steps: Long? = null,
    val totalCalories: Double? = null,
    val activeCalories: Double? = null,
    val avgHeartRate: Long? = null,
    val minHeartRate: Long? = null,
    val maxHeartRate: Long? = null,
    val sleep: Duration? = null,
    val exercise: Duration? = null,
)

/** A body-composition reading, newest first when listed. */
data class BodyReading(
    val time: Instant,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val source: String? = null,
)

/** A blood pressure reading. */
data class BloodPressureReading(
    val time: Instant,
    val systolic: Double,
    val diastolic: Double,
    val source: String? = null,
) {
    /**
     * Rough categorisation using the American Heart Association bands.
     * Informational only — not a diagnosis.
     */
    val category: String
        get() = when {
            systolic >= 180 || diastolic >= 120 -> "Crisis"
            systolic >= 140 || diastolic >= 90 -> "Stage 2"
            systolic >= 130 || diastolic >= 80 -> "Stage 1"
            systolic >= 120 -> "Elevated"
            else -> "Normal"
        }
}

/** Everything the dashboard needs, assembled in one pass. */
data class HealthSnapshot(
    val today: DaySummary = DaySummary(),
    val lastSevenDays: List<Pair<java.time.LocalDate, DaySummary>> = emptyList(),
    val latestBody: BodyReading? = null,
    val previousBody: BodyReading? = null,
    val bodyHistory: List<BodyReading> = emptyList(),
    val latestBloodPressure: BloodPressureReading? = null,
    val bloodPressureHistory: List<BloodPressureReading> = emptyList(),
) {
    /** Mean daily burn across whatever days actually reported data. */
    val weeklyAvgCalories: Double?
        get() {
            val values = lastSevenDays.mapNotNull { it.second.totalCalories }
            return if (values.isEmpty()) null else values.average()
        }

    val weeklyAvgSteps: Long?
        get() {
            val values = lastSevenDays.mapNotNull { it.second.steps }
            return if (values.isEmpty()) null else values.average().toLong()
        }

    /** Body fat change since the previous reading, in percentage points. */
    val bodyFatDelta: Double?
        get() {
            val now = latestBody?.bodyFatPercent ?: return null
            val before = previousBody?.bodyFatPercent ?: return null
            return now - before
        }

    val weightDelta: Double?
        get() {
            val now = latestBody?.weightKg ?: return null
            val before = previousBody?.weightKg ?: return null
            return now - before
        }

    /** Mean systolic/diastolic over the loaded history. */
    val avgBloodPressure: Pair<Double, Double>?
        get() {
            if (bloodPressureHistory.isEmpty()) return null
            return bloodPressureHistory.map { it.systolic }.average() to
                bloodPressureHistory.map { it.diastolic }.average()
        }
}

/** What state Health Connect itself is in on this device. */
sealed interface AvailabilityState {
    data object Available : AvailabilityState
    data object NeedsProviderUpdate : AvailabilityState
    data object NotSupported : AvailabilityState
}

/** Top-level UI state. */
sealed interface DashboardState {
    data object Loading : DashboardState
    data class Unavailable(val reason: AvailabilityState) : DashboardState
    data object NeedsPermissions : DashboardState
    data class Ready(val snapshot: HealthSnapshot, val hasAnyData: Boolean) : DashboardState
    data class Error(val message: String) : DashboardState
}
