package com.vitals.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vitals.app.data.AvailabilityState
import com.vitals.app.data.DashboardState
import com.vitals.app.data.HealthConnectManager
import com.vitals.app.data.food.FoodEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    val healthConnect = HealthConnectManager(app)

    private val vitalsApp = app as VitalsApp

    private val _state = MutableStateFlow<DashboardState>(DashboardState.Loading)
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _scanningVoice = MutableStateFlow(false)
    val scanningVoice: StateFlow<Boolean> = _scanningVoice.asStateFlow()

    /**
     * Start of "today", recomputed on every refresh. Evaluating this once at
     * construction would pin the food card to whatever day the process started
     * on, so an app left running overnight would keep showing yesterday's meals
     * against today's calorie burn.
     */
    private val dayStart = MutableStateFlow(startOfToday())

    /** Today's food entries, straight from Room — updates the moment one lands. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val todayFood: StateFlow<List<FoodEntry>> =
        dayStart
            .flatMapLatest { vitalsApp.database.foodDao().entriesSince(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun startOfToday(): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()
    }

    fun hasAudioPermission() = vitalsApp.pipeline.hasAudioPermission()

    init {
        refresh()
        watchForRecordings()
    }

    /**
     * Live MediaStore updates while this screen exists.
     *
     * MediaStore fires several times for a single new file — the row is
     * inserted, then updated as it's scanned for duration and metadata — so the
     * debounce collapses that burst into one scan. `scanVoiceNotes` also guards
     * against overlapping runs, which covers anything the debounce lets through.
     */
    @OptIn(FlowPreview::class)
    private fun watchForRecordings() {
        viewModelScope.launch {
            vitalsApp.scanner.changes()
                .debounce(2_000)
                .collect { if (hasAudioPermission()) scanVoiceNotes() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            dayStart.value = startOfToday()

            val availability = healthConnect.availability()
            if (availability != AvailabilityState.Available) {
                _state.value = DashboardState.Unavailable(availability)
                return@launch
            }

            _refreshing.value = true
            try {
                if (!healthConnect.hasAnyPermission()) {
                    _state.value = DashboardState.NeedsPermissions
                    return@launch
                }

                val snapshot = healthConnect.loadSnapshot()
                val hasData = snapshot.lastSevenDays.any { (_, day) ->
                    day.steps != null || day.totalCalories != null || day.avgHeartRate != null
                } || snapshot.latestBody != null || snapshot.latestBloodPressure != null

                _state.value = DashboardState.Ready(snapshot, hasData)
            } catch (t: Throwable) {
                _state.value = DashboardState.Error(
                    t.message ?: "Couldn't read from Health Connect."
                )
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** The user supplying a calorie count neither food database had. */
    fun confirmEntry(id: Long, kcal: Double) {
        viewModelScope.launch { vitalsApp.database.foodDao().confirmEntry(id, kcal) }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { vitalsApp.database.foodDao().deleteEntry(id) }
    }

    /** Pulls in any voice notes recorded since the last scan. */
    fun scanVoiceNotes() {
        if (_scanningVoice.value) return
        viewModelScope.launch {
            _scanningVoice.value = true
            try {
                vitalsApp.pipeline.run()
            } catch (_: Throwable) {
                // The card shows what did land; a failed scan retries on the next tick.
            } finally {
                _scanningVoice.value = false
            }
        }
    }
}
