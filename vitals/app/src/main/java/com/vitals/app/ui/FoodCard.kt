package com.vitals.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitals.app.data.food.FoodEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What I ate today.
 *
 * The transcript of each note is shown under the parsed food, because voice
 * logging fails in ways the user needs to be able to see: if "a banana" came
 * back as "a bandana", the number is wrong and only the transcript explains why.
 */
@Composable
fun FoodCard(
    entries: List<FoodEntry>,
    burnedToday: Double?,
    hasAudioPermission: Boolean,
    scanning: Boolean,
    onGrantAudio: () -> Unit,
    onScan: () -> Unit,
    onConfirmEntry: (Long, Double) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
    val zone = ZoneId.systemDefault()

    // Unreviewed items are excluded from the running total. Counting a number
    // nobody has confirmed would make the balance quietly wrong.
    val intake = entries.filterNot { it.needsReview }.sumOf { it.kcal }
    val unresolved = entries.count { it.needsReview }

    var reviewing by remember { mutableStateOf<FoodEntry?>(null) }

    reviewing?.let { entry ->
        CalorieInputDialog(
            entry = entry,
            onDismiss = { reviewing = null },
            onConfirm = { kcal ->
                onConfirmEntry(entry.id, kcal)
                reviewing = null
            },
            onDelete = {
                onDeleteEntry(entry.id)
                reviewing = null
            },
        )
    }

    SectionCard(
        title = "What I ate today",
        subtitle = when {
            !hasAudioPermission -> "Voice logging needs access to your recordings"
            entries.isEmpty() -> "Nothing logged yet — record a note on your watch"
            unresolved > 0 -> "$unresolved item${if (unresolved == 1) "" else "s"} need your input"
            else -> "${entries.size} item${if (entries.size == 1) "" else "s"} logged by voice"
        },
        modifier = modifier,
    ) {
        if (!hasAudioPermission) {
            Text(
                "Say something like \"I just ate a banana\" into your watch's voice " +
                    "recorder. The note syncs to your phone and gets logged here " +
                    "automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onGrantAudio) { Text("Allow access to recordings") }
            return@SectionCard
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatTile(
                label = "Eaten",
                value = intake.roundToInt().toString(),
                unit = "kcal",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Burned",
                value = burnedToday?.roundToInt()?.toString() ?: "–",
                unit = "kcal",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Balance",
                value = burnedToday?.let {
                    val net = intake - it
                    (if (net > 0) "+" else "−") + kotlin.math.abs(net).roundToInt()
                } ?: "–",
                unit = "kcal",
                delta = burnedToday?.let { if (intake - it < 0) "deficit" else "surplus" },
                deltaColor = burnedToday?.let {
                    if (intake - it < 0) statusColors.good else MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                FoodRow(
                    entry = entry,
                    time = timeFormat.format(
                        Instant.ofEpochSecond(entry.loggedAtEpochSecond).atZone(zone)
                    ),
                    onReview = { reviewing = entry },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onScan,
            enabled = !scanning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (scanning) "Checking recordings…" else "Check for new voice notes")
        }
    }
}

@Composable
private fun FoodRow(entry: FoodEntry, time: String, onReview: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (entry.needsReview) Modifier.clickable(onClick = onReview) else Modifier)
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.foodName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(time)
                        if (entry.quantityText.isNotBlank()) append(" · ${entry.quantityText}")
                        if (entry.isDrink) append(" · drink")
                        append(" · ${sourceLabel(entry.source)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (entry.needsReview) {
                Text(
                    text = "Add kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = "${entry.kcal.roundToInt()} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = "“${entry.transcript}”",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Asks for a number the databases couldn't supply, seeded with the model's
 * guess so the common case is one tap rather than research.
 */
@Composable
private fun CalorieInputDialog(
    entry: FoodEntry,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(entry.id) {
        mutableStateOf(entry.kcal.takeIf { it > 0 }?.roundToInt()?.toString() ?: "")
    }
    val value = text.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.foodName) },
        text = {
            Column {
                Text(
                    "Neither food database had this one, and the estimate wasn't " +
                        "confident enough to use. How many calories was it?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "“${entry.transcript}”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit) },
                    label = { Text("Calories") },
                    suffix = { Text("kcal") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let(onConfirm) },
                enabled = value != null && value > 0,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("Remove") }
        },
    )
}

private fun sourceLabel(source: String) = when (source) {
    "USDA" -> "USDA"
    "OPEN_FOOD_FACTS" -> "Open Food Facts"
    "ESTIMATE" -> "estimated"
    "USER" -> "your entry"
    else -> "needs input"
}
