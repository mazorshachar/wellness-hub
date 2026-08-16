package com.vitals.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitals.app.data.AvailabilityState
import com.vitals.app.data.BloodPressureReading
import com.vitals.app.data.DashboardState
import com.vitals.app.data.HealthSnapshot
import com.vitals.app.data.food.FoodEntry
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    refreshing: Boolean,
    foodEntries: List<FoodEntry>,
    hasAudioPermission: Boolean,
    scanningVoice: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onScanVoiceNotes: () -> Unit,
    onConfirmEntry: (Long, Double) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onInstallProvider: () -> Unit,
    onRefresh: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(VitalsTab.Today) }

    // A considered-but-not-eaten food. Lives here rather than in the ViewModel
    // because it is a question about the screen, not a fact about the day, and
    // it should not survive leaving the app.
    var projection by remember { mutableStateOf<Projection?>(null) }
    var whatIfOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            VitalsNavigationBar(selected = tab, onSelect = { tab = it })
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Vitals",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        Text("↻", style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when (state) {
            is DashboardState.Loading -> CenteredBox(Modifier.padding(padding)) {
                CircularProgressIndicator()
            }

            is DashboardState.Unavailable -> CenteredMessage(
                modifier = Modifier.padding(padding),
                title = when (state.reason) {
                    AvailabilityState.NeedsProviderUpdate -> "Health Connect needs an update"
                    else -> "Health Connect isn't available"
                },
                body = when (state.reason) {
                    AvailabilityState.NeedsProviderUpdate ->
                        "Update Health Connect from the Play Store, then come back."
                    else ->
                        "This device doesn't support Health Connect. It requires Android 9 or newer, " +
                            "and on Android 13 and below the Health Connect app must be installed."
                },
                actionLabel = "Open Play Store",
                onAction = onInstallProvider,
            )

            is DashboardState.NeedsPermissions -> CenteredMessage(
                modifier = Modifier.padding(padding),
                title = "Connect your health data",
                body = "Vitals reads steps, workouts, heart rate, sleep, weight, body fat and " +
                    "blood pressure from Health Connect — whatever your watch, scale and apps " +
                    "have already written there.\n\nIt is read-only. Nothing is written back, " +
                    "and nothing leaves your phone.",
                actionLabel = "Choose what to share",
                onAction = onRequestPermissions,
                secondaryLabel = "Open Health Connect settings",
                onSecondary = onOpenSettings,
            )

            is DashboardState.Error -> CenteredMessage(
                modifier = Modifier.padding(padding),
                title = "Something went wrong",
                body = state.message,
                actionLabel = "Try again",
                onAction = onRefresh,
            )

            is DashboardState.Ready -> when (tab) {
                VitalsTab.Today -> SnapshotList(
                    snapshot = state.snapshot,
                    hasAnyData = state.hasAnyData,
                    foodEntries = foodEntries,
                    hasAudioPermission = hasAudioPermission,
                    scanningVoice = scanningVoice,
                    projection = projection,
                    onOpenWhatIf = { whatIfOpen = true },
                    onClearProjection = { projection = null },
                    onRequestAudioPermission = onRequestAudioPermission,
                    onScanVoiceNotes = onScanVoiceNotes,
                    onConfirmEntry = onConfirmEntry,
                    onDeleteEntry = onDeleteEntry,
                    contentPadding = padding,
                    onOpenSettings = onOpenSettings,
                )

                VitalsTab.Nutrition -> ComingSoon(
                    title = "Nutrition",
                    body = "Sugar, protein, sodium, iron and the rest as bars against " +
                        "your targets, with a 3-dot menu to pick which ones you follow.",
                    contentPadding = padding,
                )

                VitalsTab.Supplements -> ComingSoon(
                    title = "Supplements",
                    body = "Per-pill labels from a photo, one-tap templates like " +
                        "\"lunch supplements\", and warnings when two things you took " +
                        "together cancel each other out.",
                    contentPadding = padding,
                )

                VitalsTab.Goal -> ComingSoon(
                    title = "Goal",
                    body = "Steady, moderate or aggressive loss — or your own number — " +
                        "worked out from your age, weight and height, with a floor so " +
                        "the target never goes somewhere unsafe.",
                    contentPadding = padding,
                )

                VitalsTab.Settings -> ComingSoon(
                    title = "Settings",
                    body = "Health Connect permissions, voice logging, and the dial's " +
                        "own look — sweep, thickness and colours.",
                    contentPadding = padding,
                )
            }
        }
    }

    if (whatIfOpen) {
        WhatIfSheet(
            onDismiss = { whatIfOpen = false },
            onProject = { label, kcal ->
                projection = Projection(label, kcal)
                whatIfOpen = false
            },
        )
    }
}

/** A food being considered, not eaten. */
data class Projection(val label: String, val kcal: Float)

@Composable
private fun SnapshotList(
    snapshot: HealthSnapshot,
    hasAnyData: Boolean,
    foodEntries: List<FoodEntry>,
    hasAudioPermission: Boolean,
    scanningVoice: Boolean,
    projection: Projection?,
    onOpenWhatIf: () -> Unit,
    onClearProjection: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onScanVoiceNotes: () -> Unit,
    onConfirmEntry: (Long, Double) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
) {
    val dayFormat = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val dateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val zone = ZoneId.systemDefault()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Calorie balance dial ---------------------------------------
        // Burn comes from Health Connect rather than an estimate. If nothing
        // has written it yet the dial would be measuring against zero, which
        // reads as a huge surplus — so the card says so instead of lying.
        item {
            val eaten = foodEntries.sumOf { it.kcal }.toFloat()
            val burned = snapshot.today.totalCalories?.toFloat()

            SectionCard(
                title = "Calorie balance",
                subtitle = burned?.let {
                    "${eaten.roundToInt()} eaten · ${it.roundToInt()} burned"
                } ?: "Waiting on burn data",
            ) {
                if (burned == null) {
                    Text(
                        "Nothing has written calories burned to Health Connect today. " +
                            "Samsung Health usually fills this in after the first sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val net = eaten - burned
                    CalorieDial(
                        net = net,
                        projected = projection?.let { net + it.kcal },
                        goalDeficit = null,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (projection != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${projection.label} · +${projection.kcal.roundToInt()} kcal",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = onClearProjection) { Text("Clear") }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onOpenWhatIf,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("If I eat this…")
                        }
                    }
                }
            }
        }

        if (!hasAnyData) {
            item {
                SectionCard(
                    title = "No data yet",
                    subtitle = "Health Connect is connected but empty for the last week.",
                ) {
                    Text(
                        "Open Samsung Health, Fitbit, or your scale's app and make sure each one " +
                            "is allowed to write to Health Connect. Data usually appears within " +
                            "a few minutes of the next sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onOpenSettings) {
                        Text("Open Health Connect")
                    }
                }
            }
        }

        // ---- Food, logged by voice ---------------------------------------
        item {
            FoodCard(
                entries = foodEntries,
                burnedToday = snapshot.today.totalCalories,
                hasAudioPermission = hasAudioPermission,
                scanning = scanningVoice,
                onGrantAudio = onRequestAudioPermission,
                onScan = onScanVoiceNotes,
                onConfirmEntry = onConfirmEntry,
                onDeleteEntry = onDeleteEntry,
            )
        }

        // ---- Body composition -------------------------------------------
        item {
            SectionCard(
                title = "Body composition",
                subtitle = snapshot.latestBody?.let {
                    "Last reading ${dateFormat.format(it.time.atZone(zone))}"
                } ?: "No weight or body fat readings found",
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatTile(
                        label = "Body fat",
                        value = snapshot.latestBody?.bodyFatPercent
                            ?.let { format1(it) } ?: "–",
                        unit = "%",
                        delta = snapshot.bodyFatDelta?.let { signed1(it) + " pts" },
                        deltaColor = trendColor(snapshot.bodyFatDelta, lowerIsBetter = true),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Weight",
                        value = snapshot.latestBody?.weightKg?.let { format1(it) } ?: "–",
                        unit = "kg",
                        delta = snapshot.weightDelta?.let { signed1(it) + " kg" },
                        deltaColor = trendColor(snapshot.weightDelta, lowerIsBetter = true),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (snapshot.bodyHistory.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${snapshot.bodyHistory.size} readings in the last 30 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- Blood pressure ----------------------------------------------
        item {
            val latest = snapshot.latestBloodPressure
            SectionCard(
                title = "Blood pressure",
                subtitle = latest?.let {
                    "Last reading ${dateFormat.format(it.time.atZone(zone))}"
                } ?: "No readings found",
            ) {
                if (latest == null) {
                    Text(
                        "Nothing has written blood pressure to Health Connect. A connected cuff " +
                            "(Withings, Omron) or manual entry in a supported app will show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        StatTile(
                            label = "Latest",
                            value = "${latest.systolic.roundToInt()}/${latest.diastolic.roundToInt()}",
                            unit = "mmHg",
                            modifier = Modifier.weight(1f),
                        )
                        snapshot.avgBloodPressure?.let { (sys, dia) ->
                            StatTile(
                                label = "30-day average",
                                value = "${sys.roundToInt()}/${dia.roundToInt()}",
                                unit = "mmHg",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    StatusPill(latest.category, bloodPressureColor(latest))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Category follows the AHA bands. Informational only — it isn't a diagnosis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- Energy -------------------------------------------------------
        item {
            SectionCard(
                title = "Energy burned",
                subtitle = "Total calories per day, last 7 days",
            ) {
                WeekBars(
                    values = snapshot.lastSevenDays.map { it.second.totalCalories },
                    dayLabels = snapshot.lastSevenDays.map { dayFormat.format(it.first) },
                    formatValue = { it.roundToInt().toString() },
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatTile(
                        label = "Today total",
                        value = snapshot.today.totalCalories?.roundToInt()?.toString() ?: "–",
                        unit = "kcal",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Today active",
                        value = snapshot.today.activeCalories?.roundToInt()?.toString() ?: "–",
                        unit = "kcal",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "7-day avg",
                        value = snapshot.weeklyAvgCalories?.roundToInt()?.toString() ?: "–",
                        unit = "kcal",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ---- Movement -----------------------------------------------------
        item {
            SectionCard(
                title = "Movement",
                subtitle = "Steps per day, last 7 days",
            ) {
                WeekBars(
                    values = snapshot.lastSevenDays.map { it.second.steps?.toDouble() },
                    dayLabels = snapshot.lastSevenDays.map { dayFormat.format(it.first) },
                    formatValue = { compact(it) },
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatTile(
                        label = "Today",
                        value = snapshot.today.steps?.toString() ?: "–",
                        unit = "steps",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "7-day avg",
                        value = snapshot.weeklyAvgSteps?.toString() ?: "–",
                        unit = "steps",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Workout today",
                        value = snapshot.today.exercise?.let { formatDuration(it) } ?: "–",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ---- Recovery -----------------------------------------------------
        item {
            SectionCard(
                title = "Heart & recovery",
                subtitle = "Today",
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatTile(
                        label = "Avg HR",
                        value = snapshot.today.avgHeartRate?.toString() ?: "–",
                        unit = "bpm",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Resting low",
                        value = snapshot.today.minHeartRate?.toString() ?: "–",
                        unit = "bpm",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Sleep",
                        value = snapshot.today.sleep?.let { formatDuration(it) } ?: "–",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "Read-only. Vitals never writes to Health Connect and never sends your data " +
                    "off this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Manage permissions in Health Connect")
            }
        }
    }
}

@Composable
private fun CenteredBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction) { Text(actionLabel) }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}

// ------------------------------------------------------------------ helpers

private fun format1(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

private fun signed1(value: Double): String {
    val sign = if (value > 0) "+" else if (value < 0) "−" else "±"
    return sign + String.format(Locale.getDefault(), "%.1f", abs(value))
}

private fun compact(value: Double): String =
    if (value >= 10_000) "${(value / 1000).roundToInt()}k" else value.roundToInt().toString()

private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/** Movement in the goal's direction gets called out; everything else stays neutral ink. */
@Composable
private fun trendColor(delta: Double?, lowerIsBetter: Boolean) = when {
    delta == null || abs(delta) < 0.05 -> MaterialTheme.colorScheme.onSurfaceVariant
    (delta < 0) == lowerIsBetter -> statusColors.good
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun bloodPressureColor(reading: BloodPressureReading) = when (reading.category) {
    "Normal" -> statusColors.good
    "Elevated" -> statusColors.warning
    "Stage 1" -> statusColors.serious
    else -> statusColors.critical
}
