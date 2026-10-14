package dev.neumorphic.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.neumorphic.LocalNeumorphicColors
import dev.neumorphic.NeumorphicColors
import dev.neumorphic.NeumorphicShape
import dev.neumorphic.animation.NeumorphicMotion
import dev.neumorphic.haptics.rememberNeumorphicHaptics
import dev.neumorphic.neumorphicInset
import dev.neumorphic.neumorphicRaised
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Reusable Tactile Neumorphic Rotary Dial / Knob with smooth spring interpolation and haptic tick feedback.
 */
@Composable
fun NeumorphicRotaryDial(
    value: Float, // 0f .. 1f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    enabled: Boolean = true,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    testTag: String = "neumorphic_rotary_dial"
) {
    val haptics = rememberNeumorphicHaptics()
    val shape = NeumorphicShape.Circle
    val startAngle = 135f
    val sweepAngle = 270f
    val tickCount = 12
    var lastTickIndex by remember { mutableIntStateOf((value.coerceIn(0f, 1f) * tickCount).roundToInt()) }

    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "rotaryAnimatedVal"
    )

    val currentAngle = startAngle + (animatedValue * sweepAngle)

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val touchVector = change.position - center
                    var touchAngle = (atan2(touchVector.y, touchVector.x) * (180f / PI.toFloat()))
                    if (touchAngle < 0) touchAngle += 360f

                    // Normalize angle
                    var normalizedAngle = touchAngle - startAngle
                    if (normalizedAngle < 0) normalizedAngle += 360f

                    if (normalizedAngle <= sweepAngle + 30f) {
                        val newValue = (normalizedAngle / sweepAngle).coerceIn(0f, 1f)
                        val newTick = (newValue * tickCount).roundToInt()
                        if (newTick != lastTickIndex) {
                            lastTickIndex = newTick
                            haptics.tick()
                        }
                        onValueChange(newValue)
                    }
                }
            }
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { role = Role.Button }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        // Outer arc gauge background and tick marks
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 5.dp.toPx()
            val arcSize = Size(this.size.width - 24.dp.toPx(), this.size.height - 24.dp.toPx())
            val topLeft = Offset(12.dp.toPx(), 12.dp.toPx())
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outerRadius = (this.size.width - 12.dp.toPx()) / 2f

            // Radial tick marks
            val tickCount = 12
            for (i in 0..tickCount) {
                val tickFraction = i.toFloat() / tickCount
                val tickAngle = startAngle + (tickFraction * sweepAngle)
                val rad = (tickAngle - 90f) * (PI / 180f).toFloat()

                val innerDist = outerRadius - 4.dp.toPx()
                val outerDist = outerRadius + 2.dp.toPx()

                val p1 = Offset(center.x + innerDist * cos(rad), center.y + innerDist * sin(rad))
                val p2 = Offset(center.x + outerDist * cos(rad), center.y + outerDist * sin(rad))

                val isPassed = animatedValue >= tickFraction
                val tickColor = if (isPassed) colors.accentPrimary else colors.textSecondary.copy(alpha = 0.25f)
                drawLine(
                    color = tickColor,
                    start = p1,
                    end = p2,
                    strokeWidth = if (isPassed) 2.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Inactive Track
            drawArc(
                color = colors.textSecondary.copy(alpha = 0.18f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Active fill
            drawArc(
                brush = colors.accentGradient,
                startAngle = startAngle,
                sweepAngle = sweepAngle * animatedValue,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }

        // Raised Central Knob with tactile depth
        Box(
            modifier = Modifier
                .size(size * 0.65f)
                .neumorphicRaised(colors = colors, shape = shape, elevation = 5.dp)
                .clip(CircleShape)
                .background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            // Indicator dot on the knob
            val indicatorAngleRad = (currentAngle - 90f) * (PI / 180f).toFloat()
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = this.size.width / 2f
                val center = Offset(radius, radius)
                val dotDist = radius * 0.65f
                val dotCenter = Offset(
                    center.x + dotDist * cos(indicatorAngleRad),
                    center.y + dotDist * sin(indicatorAngleRad)
                )

                drawCircle(
                    color = colors.accentPrimary,
                    radius = 4.5.dp.toPx(),
                    center = dotCenter
                )
            }
        }
    }
}
