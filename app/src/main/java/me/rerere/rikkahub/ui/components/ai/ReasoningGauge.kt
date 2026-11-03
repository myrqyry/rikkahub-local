package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import me.rerere.ai.core.ReasoningLevel

/**
 * Static final dial geometry adapted from Line MD gauge icons, MIT license.
 * The needle moves only when the selected level changes (never loops).
 *
 * Source: https://icon-sets.iconify.design/line-md/gauge-empty/
 *         https://icon-sets.iconify.design/line-md/gauge-low/
 *         https://icon-sets.iconify.design/line-md/gauge/
 *         https://icon-sets.iconify.design/line-md/gauge-full/
 * Copyright 2020 Vjacheslav Trushkin. Notice: docs/licenses/line-md.txt
 *
 * SVG SMIL animations do not execute in a Compose ImageVector; this is a
 * native Canvas adaptation using the source SVG's 24x24 geometry and angles.
 */
internal fun ReasoningLevel.gaugeNeedleDegrees(): Float? = when (this) {
    ReasoningLevel.OFF -> 15f
    ReasoningLevel.AUTO -> null // Unspecified effort: a neutral dial with A, not Medium.
    ReasoningLevel.LOW -> 65f
    ReasoningLevel.MEDIUM -> 115f
    ReasoningLevel.HIGH, ReasoningLevel.XHIGH -> 165f
}

internal fun ReasoningLevel.gaugeShowsAutoMarker(): Boolean = this == ReasoningLevel.AUTO
internal fun ReasoningLevel.gaugeShowsExtraHighMarker(): Boolean = this == ReasoningLevel.XHIGH

@Composable
internal fun ReasoningGauge(
    level: ReasoningLevel,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val degrees by animateFloatAsState(
        targetValue = level.gaugeNeedleDegrees() ?: 90f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "Thinking effort needle",
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val u = size.minDimension / 24f
            val left = (size.width - 24f * u) / 2f
            val top = (size.height - 24f * u) / 2f
            fun point(x: Float, y: Float) = Offset(left + x * u, top + y * u)
            val center = point(12f, 12f)

            drawCircle(
                color = tint,
                radius = 9f * u,
                center = center,
                style = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // Gauge plinth from the final, fully rendered Line MD SVG shape.
            val plinth = Path().apply {
                moveTo(left + 12f * u, top + 16f * u)
                cubicTo(left + 9.41f * u, top + 16f * u,
                    left + 7.15f * u, top + 17.21f * u,
                    left + 5.94f * u, top + 19f * u)
                lineTo(left + 12f * u, top + 21f * u)
                lineTo(left + 18.06f * u, top + 19f * u)
                cubicTo(left + 16.85f * u, top + 17.21f * u,
                    left + 14.59f * u, top + 16f * u,
                    left + 12f * u, top + 16f * u)
                close()
            }
            drawPath(plinth, tint)

            for (angle in listOf(15f, 65f, 115f, 165f)) {
                val rad = Math.toRadians(angle.toDouble())
                val x = 12f - (5f * cos(rad).toFloat())
                val y = 12f - (5f * sin(rad).toFloat())
                drawCircle(tint, radius = 1f * u, center = point(x, y))
            }

            if (level.gaugeNeedleDegrees() != null) {
                val rad = Math.toRadians(degrees.toDouble())
                val end = point(
                    12f - 5.5f * cos(rad).toFloat(),
                    12f - 5.5f * sin(rad).toFloat(),
                )
                drawLine(tint, start = center, end = end, strokeWidth = 2f * u,
                    cap = StrokeCap.Round)
                drawCircle(tint, radius = 1f * u, center = center)
            }
        }

        if (level.gaugeShowsAutoMarker()) {
            Text(
                text = "A",
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = (-1).dp),
            )
        }
        if (level.gaugeShowsExtraHighMarker()) {
            Text(
                text = "+",
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 1.dp, y = (-2).dp),
            )
        }
    }
}
