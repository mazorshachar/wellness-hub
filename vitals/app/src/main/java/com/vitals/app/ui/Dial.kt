package com.vitals.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Geometry and thresholds ported verbatim from the HTML prototype, so the shape
 * you signed off on is the shape that ships.
 *
 * Angles here use the prototype's convention: 0° is straight up, positive is
 * clockwise. Compose's drawArc measures from 3 o'clock, hence the -90 offset in
 * [arc] — keeping the conversion in one place is what stops the needle and the
 * fill from disagreeing.
 */
private const val SWEEP = 240f          // total arc, degrees
private const val HALF = SWEEP / 2f
private const val SCALE = 1500f         // kcal at each end of the arc
private const val DEAD = 150f           // ±band treated as "maintaining"
private val THICKNESS = 18.dp
private val DIAL_HEIGHT = 232.dp

/** The three zone colors, resolved against the current light/dark scheme. */
private data class DialPalette(
    val deficit: Color,
    val neutral: Color,
    val surplus: Color,
    val track: Color,
    val ink: Color,
    val surface: Color,
)

private fun angleFor(value: Float): Float =
    (value.coerceIn(-SCALE, SCALE) / SCALE) * HALF

private fun DialPalette.zone(value: Float): Color = when {
    abs(value) <= DEAD -> neutral
    value < 0f -> deficit
    else -> surplus
}

private fun stateWord(value: Float): String = when {
    abs(value) <= DEAD -> "MAINTAINING"
    value < 0f -> "DEFICIT"
    else -> "SURPLUS"
}

private fun signed(value: Float): String {
    val n = abs(value).roundToInt()
    return when {
        value > 0f -> "+$n"
        value < 0f -> "−$n"   // real minus sign, not a hyphen
        else -> "$n"
    }
}

/** Point on the dial circle, in the prototype's angle convention. */
private fun polar(cx: Float, cy: Float, r: Float, deg: Float): Offset {
    val a = (deg - 90f) * PI.toFloat() / 180f
    return Offset(cx + r * cos(a), cy + r * sin(a))
}

/**
 * Calorie balance for the day. Negative is a deficit and reads green, because
 * the goal is fat loss — the sign convention is deliberately the opposite of a
 * progress bar and the words on the face say so.
 *
 * @param net eaten minus burned, kcal
 * @param projected where the day would land if [net] plus a considered food were
 *   eaten. Null when nothing is being considered.
 * @param goalDeficit the daily deficit target, drawn as a notch. Null until a
 *   goal has been set.
 */
@Composable
fun CalorieDial(
    net: Float,
    projected: Float?,
    goalDeficit: Float?,
    modifier: Modifier = Modifier,
) {
    val status = statusColors
    val scheme = MaterialTheme.colorScheme
    val palette = DialPalette(
        deficit = status.good,
        neutral = scheme.onSurfaceVariant,
        surplus = status.serious,
        track = scheme.surfaceVariant,
        ink = scheme.onSurfaceVariant,
        surface = scheme.surface,
    )

    // The needle always reports the day as it stands. The readout follows the
    // projection when one is active, because that is the number being decided on.
    val shown = projected ?: net

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(DIAL_HEIGHT)
        ) {
            val t = THICKNESS.toPx()
            val pad = 26.dp.toPx()

            // Arc spans 1.732R horizontally and 1.5R vertically at a 240° sweep.
            // Taking the smaller of the two fits it without clipping on any width.
            val r = min(
                (size.width - t - 2 * pad) / 1.732f,
                (size.height - t - 2 * pad) / 1.5f,
            )
            val cx = size.width / 2f
            val cy = pad + t / 2f + r

            drawDial(palette, cx, cy, r, t, net, projected, goalDeficit)
        }

        // Centre readout. Drawn as real text rather than into the canvas so it
        // scales with the user's font size setting.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = DIAL_HEIGHT * 0.42f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = signed(shown),
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.zone(shown),
            )
            Text(
                text = stateWord(shown),
                style = MaterialTheme.typography.labelSmall,
                color = palette.ink,
            )
            if (projected != null) {
                Text(
                    text = "if you eat this",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Text(
            text = "−1500",
            style = MaterialTheme.typography.labelSmall,
            color = palette.ink,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        Text(
            text = "+1500",
            style = MaterialTheme.typography.labelSmall,
            color = palette.ink,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

private fun DrawScope.arc(
    cx: Float,
    cy: Float,
    r: Float,
    from: Float,
    to: Float,
    color: Color,
    width: Float,
    cap: StrokeCap,
    alpha: Float = 1f,
    dashed: Boolean = false,
) {
    if (abs(to - from) < 0.01f) return
    drawArc(
        color = color,
        startAngle = from - 90f,
        sweepAngle = to - from,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r),
        size = Size(r * 2, r * 2),
        alpha = alpha,
        style = Stroke(
            width = width,
            cap = cap,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 10f)) else null,
        ),
    )
}

private fun DrawScope.drawDial(
    p: DialPalette,
    cx: Float,
    cy: Float,
    r: Float,
    t: Float,
    net: Float,
    projected: Float?,
    goalDeficit: Float?,
) {
    // ---- Track: the full range, greyed. The coloured fill sits on top of it,
    // so an empty day still shows the shape of the scale.
    drawArc(
        brush = Brush.horizontalGradient(
            0f to p.track,
            1f to p.track,
            startX = cx - r,
            endX = cx + r,
        ),
        startAngle = -HALF - 90f,
        sweepAngle = SWEEP,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = t, cap = StrokeCap.Round),
    )

    val netAngle = angleFor(net)

    // ---- Projection band: from where the day is now, to where it would land.
    // Drawn before the fill so the solid current value stays legible on top.
    if (projected != null) {
        arc(
            cx, cy, r,
            from = netAngle,
            to = angleFor(projected),
            color = p.zone(projected),
            width = t,
            cap = StrokeCap.Butt,
            alpha = 0.35f,
            dashed = true,
        )
    }

    // ---- Fill: centre outwards to the current value.
    arc(
        cx, cy, r,
        from = 0f,
        to = netAngle,
        color = p.zone(net),
        width = t,
        cap = StrokeCap.Round,
    )

    // ---- Dead-zone edges: where "maintaining" stops and a real trend starts.
    listOf(-DEAD, DEAD).forEach { v ->
        val g = angleFor(v)
        drawLine(
            color = p.surface,
            start = polar(cx, cy, r - t / 2f - 1f, g),
            end = polar(cx, cy, r + t / 2f + 1f, g),
            strokeWidth = 2.dp.toPx(),
            alpha = 0.9f,
        )
    }

    // ---- Ticks at thirds of the scale.
    var v = -SCALE
    while (v <= SCALE + 1f) {
        val g = angleFor(v)
        drawLine(
            color = p.ink,
            start = polar(cx, cy, r - t / 2f - 7f, g),
            end = polar(cx, cy, r - t / 2f - 2f, g),
            strokeWidth = 1.5.dp.toPx(),
            alpha = 0.45f,
        )
        v += SCALE / 3f
    }

    // ---- Goal notch. Negative angle, because a deficit target sits on the
    // green side — getting this sign wrong puts the goal where the surplus is.
    if (goalDeficit != null && goalDeficit > 0f) {
        val g = angleFor(-goalDeficit)
        drawLine(
            color = p.ink,
            start = polar(cx, cy, r - t / 2f - 3f, g),
            end = polar(cx, cy, r + t / 2f + 6f, g),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
            alpha = 0.75f,
        )
    }

    // ---- Needles. The ghost shows the projection, the solid one the present.
    if (projected != null) {
        needle(cx, cy, r, t, angleFor(projected), p.zone(projected), ghost = true)
    }
    needle(cx, cy, r, t, netAngle, p.zone(net), ghost = false)
}

/**
 * Stops short of the centre on purpose — a full-length needle runs straight
 * through the readout and makes the number unreadable.
 */
private fun DrawScope.needle(
    cx: Float,
    cy: Float,
    r: Float,
    t: Float,
    angle: Float,
    color: Color,
    ghost: Boolean,
) {
    val inner = polar(cx, cy, r - t / 2f - 30.dp.toPx(), angle)
    val outer = polar(cx, cy, r - t / 2f - 5.dp.toPx(), angle)
    drawLine(
        color = color,
        start = inner,
        end = outer,
        strokeWidth = if (ghost) 3.dp.toPx() else 4.5.dp.toPx(),
        cap = StrokeCap.Round,
        alpha = if (ghost) 0.5f else 1f,
        pathEffect = if (ghost) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
    )
    drawCircle(
        color = color,
        radius = if (ghost) 2.5.dp.toPx() else 3.5.dp.toPx(),
        center = outer,
        alpha = if (ghost) 0.5f else 1f,
    )
}

/** Blend helper kept for the ramp variants in the prototype. */
internal fun blend(a: Color, b: Color, fraction: Float): Color = lerp(a, b, fraction)
