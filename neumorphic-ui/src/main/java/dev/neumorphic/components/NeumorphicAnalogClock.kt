package dev.neumorphic.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.neumorphic.LocalNeumorphicColors
import dev.neumorphic.NeumorphicColors
import dev.neumorphic.NeumorphicShape
import dev.neumorphic.neumorphicInset
import dev.neumorphic.neumorphicRaised
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reusable Neumorphic Minimalist Analog Clock with spring mechanical tick animation.
 */
@Composable
fun NeumorphicAnalogClock(
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    testTag: String = "neumorphic_analog_clock"
) {
    var calendar by remember { mutableStateOf(Calendar.getInstance()) }
    var rawSecondTick by remember { mutableLongStateOf(0L) }

    // Live clock ticker
    LaunchedEffect(Unit) {
        val initialCal = Calendar.getInstance()
        val currentSec = initialCal.get(Calendar.SECOND)
        rawSecondTick = currentSec.toLong()
        while (true) {
            calendar = Calendar.getInstance()
            rawSecondTick++
            delay(1000L)
        }
    }

    val hours = calendar.get(Calendar.HOUR)
    val minutes = calendar.get(Calendar.MINUTE)
    val seconds = calendar.get(Calendar.SECOND)

    val hourAngle = (hours + minutes / 60f) * 30f // 360 / 12 = 30 deg per hr
    val minuteAngle = (minutes + seconds / 60f) * 6f // 360 / 60 = 6 deg per min

    // Spring tick with subtle overshoot
    val animatedSecondAngle by animateFloatAsState(
        targetValue = rawSecondTick * 6f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 480f),
        label = "clockSecondSpring"
    )

    val shape = NeumorphicShape.Circle

    // Raised outer ring container
    Box(
        modifier = modifier
            .size(size)
            .neumorphicRaised(colors = colors, shape = shape, elevation = 6.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        // Sunken inner dial
        Box(
            modifier = Modifier
                .size(size * 0.85f)
                .clip(CircleShape)
                .background(colors.surface)
                .neumorphicInset(colors = colors, shape = shape, depth = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val radius = this.size.width / 2f

                // Draw 12 hour subtle tick marks
                for (i in 0 until 12) {
                    val angleRad = (i * 30f - 90f) * (PI / 180f).toFloat()
                    val isMajor = i % 3 == 0
                    val tickLen = if (isMajor) radius * 0.12f else radius * 0.06f
                    val tickColor = if (isMajor) colors.textSecondary.copy(alpha = 0.5f) else colors.textSecondary.copy(alpha = 0.25f)
                    val strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()

                    val startX = center.x + (radius - tickLen - 6.dp.toPx()) * cos(angleRad)
                    val startY = center.y + (radius - tickLen - 6.dp.toPx()) * sin(angleRad)
                    val endX = center.x + (radius - 6.dp.toPx()) * cos(angleRad)
                    val endY = center.y + (radius - 6.dp.toPx()) * sin(angleRad)

                    drawLine(
                        color = tickColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                // Draw Hour Hand
                val hourRad = (hourAngle - 90f) * (PI / 180f).toFloat()
                val hourLen = radius * 0.48f
                drawLine(
                    color = colors.textSecondary,
                    start = center,
                    end = Offset(
                        center.x + hourLen * cos(hourRad),
                        center.y + hourLen * sin(hourRad)
                    ),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Draw Minute Hand
                val minRad = (minuteAngle - 90f) * (PI / 180f).toFloat()
                val minLen = radius * 0.68f
                drawLine(
                    color = colors.textPrimary,
                    start = center,
                    end = Offset(
                        center.x + minLen * cos(minRad),
                        center.y + minLen * sin(minRad)
                    ),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Draw Second Hand with physical spring overshoot
                val secRad = (animatedSecondAngle - 90f) * (PI / 180f).toFloat()
                val secLen = radius * 0.78f
                drawLine(
                    color = colors.accentPrimary,
                    start = center - Offset(radius * 0.15f * cos(secRad), radius * 0.15f * sin(secRad)),
                    end = Offset(
                        center.x + secLen * cos(secRad),
                        center.y + secLen * sin(secRad)
                    ),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Center Pin Hub
                drawCircle(
                    color = colors.accentPrimary,
                    radius = 3.5.dp.toPx(),
                    center = center
                )
            }
        }
    }
}
