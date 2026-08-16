package com.vitals.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The five destinations from the prototype. Kept as an enum rather than a
 * navigation graph because every screen shares the same dashboard state — a
 * NavHost here would mean either duplicating that state or hoisting it anyway.
 */
enum class VitalsTab(val label: String, val glyph: String) {
    Today("Today", "◉"),
    Nutrition("Nutrition", "▤"),
    Supplements("Supps", "◇"),
    Goal("Goal", "◎"),
    Settings("Settings", "⚙"),
}

@Composable
fun VitalsNavigationBar(
    selected: VitalsTab,
    onSelect: (VitalsTab) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        VitalsTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    Text(
                        tab.glyph,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                label = {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

/**
 * Stands in for a tab that hasn't been built yet. Says what is coming rather
 * than showing an empty screen, so a half-finished app doesn't read as broken.
 */
@Composable
fun ComingSoon(
    title: String,
    body: String,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
