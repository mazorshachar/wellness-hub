package com.vitals.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * One accent hue for the whole app. Charts here are single-series, so there is
 * no categorical palette — magnitude is carried by a single hue and the ink
 * tokens below stay neutral so numbers never inherit a series color.
 */
private val Teal600 = Color(0xFF0F766E)
private val Teal500 = Color(0xFF14857B)
private val Teal300 = Color(0xFF5EC5B6)
private val Teal100 = Color(0xFFD5F0EB)

/**
 * Status ramp — reserved. Never reused as a series color.
 *
 * Dark mode gets its own steps rather than an automatic flip of the light ones:
 * the light steps are chosen for contrast against white and go muddy and
 * near-illegible on the dark surface.
 */
@Immutable
data class StatusColors(
    val good: Color,
    val warning: Color,
    val serious: Color,
    val critical: Color,
)

private val LightStatus = StatusColors(
    good = Color(0xFF15803D),
    warning = Color(0xFFB45309),
    serious = Color(0xFFC2410C),
    critical = Color(0xFFB91C1C),
)

private val DarkStatus = StatusColors(
    good = Color(0xFF4ADE80),
    warning = Color(0xFFFBBF24),
    serious = Color(0xFFFB923C),
    critical = Color(0xFFF87171),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** Shorthand: `MaterialTheme.statusColors.good` */
val statusColors: StatusColors
    @Composable get() = LocalStatusColors.current

private val LightColors = lightColorScheme(
    primary = Teal600,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Color(0xFF06302C),
    secondary = Teal500,
    background = Color(0xFFF7F8F8),
    onBackground = Color(0xFF16191A),
    surface = Color.White,
    onSurface = Color(0xFF16191A),
    surfaceVariant = Color(0xFFEDEFEF),
    onSurfaceVariant = Color(0xFF5B6366),
    outline = Color(0xFFD3D8D9),
)

private val DarkColors = darkColorScheme(
    primary = Teal300,
    onPrimary = Color(0xFF04211E),
    primaryContainer = Color(0xFF10453F),
    onPrimaryContainer = Teal100,
    secondary = Teal300,
    background = Color(0xFF111414),
    onBackground = Color(0xFFEDEFEF),
    surface = Color(0xFF191D1D),
    onSurface = Color(0xFFEDEFEF),
    surfaceVariant = Color(0xFF242929),
    onSurfaceVariant = Color(0xFFA5AEAF),
    outline = Color(0xFF3A4142),
)

@Composable
fun VitalsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
