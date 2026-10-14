package dev.neumorphic.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.neumorphic.LocalNeumorphicColors
import dev.neumorphic.NeumorphicColors
import dev.neumorphic.NeumorphicShape
import dev.neumorphic.haptics.rememberNeumorphicHaptics
import dev.neumorphic.neumorphicInset
import dev.neumorphic.neumorphicRaised
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tactile slider built from the same physical grammar as [NeumorphicSwitch].
 *
 * The default thumb and rail share a height, so the slider reads as a puck travelling across a
 * touch product rather than a generic thin line with an unrelated knob. The active range is a
 * translucent emission over the surface shell to mimic a diffused LED under opaque material.
 */
@Composable
fun NeumorphicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    trackHeight: Dp = 20.dp,
    thumbSize: Dp = trackHeight,
    enabled: Boolean = true,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    testTag: String = "neumorphic_slider"
) {
    val haptics = rememberNeumorphicHaptics()
    var isDragging by remember { mutableStateOf(false) }
    val rangeSpan = valueRange.endInclusive - valueRange.start
    val normalizedValue = if (rangeSpan == 0f) {
        0f
    } else {
        ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
    }
    var lastStepIndex by remember { mutableIntStateOf((normalizedValue * 20).roundToInt()) }

    val thumbElevation by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "sliderThumbElev"
    )

    // These colors do not change while the user drags, so keep both Brush objects stable across
    // the hot recomposition path. They are rebuilt only when the active palette actually changes.
    val activeTrackBrush = remember(
        colors.accentPrimary,
        colors.accentGradientStart,
        colors.accentGradientEnd
    ) {
        Brush.horizontalGradient(
            listOf(
                colors.accentPrimary.copy(alpha = 0.42f),
                colors.accentGradientStart.copy(alpha = 0.64f),
                colors.accentGradientEnd.copy(alpha = 0.82f)
            )
        )
    }
    val thumbGlowBrush = remember(colors.accentPrimary) {
        Brush.radialGradient(
            listOf(
                colors.accentPrimary.copy(alpha = 0.72f),
                colors.accentPrimary.copy(alpha = 0.28f),
                Color.Transparent
            )
        )
    }

    val density = LocalDensity.current

    fun seekTo(xPx: Float, widthPx: Float) {
        if (widthPx <= 0f || rangeSpan == 0f) return
        val thumbPx = with(density) { thumbSize.toPx() }
        val travelPx = (widthPx - thumbPx).coerceAtLeast(1f)
        val newNorm = ((xPx - (thumbPx / 2f)) / travelPx).coerceIn(0f, 1f)
        val stepIndex = (newNorm * 20).roundToInt()
        if (stepIndex != lastStepIndex) {
            lastStepIndex = stepIndex
            haptics.tick()
        }
        onValueChange(valueRange.start + newNorm * rangeSpan)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(maxOf(48.dp, thumbSize, trackHeight))
            .alpha(if (enabled) 1f else 0.4f)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value,
                    range = valueRange,
                    steps = 0
                )
                if (enabled) {
                    setProgress { target ->
                        onValueChange(target.coerceIn(valueRange.start, valueRange.endInclusive))
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(valueRange, enabled, thumbSize) {
                if (!enabled) return@pointerInput

                // Keep tap-to-seek and horizontal dragging in one recognizer, but do not consume
                // movement until horizontal intent has beaten touch slop. Vertical movement is
                // left untouched so a parent verticalScroll/LazyColumn can claim the gesture.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val start = down.position
                    val touchSlop = viewConfiguration.touchSlop
                    var horizontalDrag = false

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                            // A parent or sibling won the gesture before we did.
                            if (!horizontalDrag && change.isConsumed) break

                            if (!change.pressed) {
                                // No drag won: this was a tap, so seek once on release. Deferring
                                // the seek prevents a vertical scroll that began on the slider from
                                // nudging the value before the parent can take over.
                                if (!horizontalDrag && !change.isConsumed) {
                                    seekTo(change.position.x, size.width.toFloat())
                                    // We accepted this gesture as a slider tap; consume the release
                                    // so a clickable ancestor cannot also fire for the same tap.
                                    change.consume()
                                }
                                break
                            }

                            if (horizontalDrag) {
                                change.consume()
                                seekTo(change.position.x, size.width.toFloat())
                                continue
                            }

                            val dx = change.position.x - start.x
                            val dy = change.position.y - start.y
                            if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                                if (abs(dx) > abs(dy)) {
                                    horizontalDrag = true
                                    isDragging = true
                                    change.consume()
                                    seekTo(change.position.x, size.width.toFloat())
                                } else {
                                    // Vertical intent wins. Leave this and all following movement
                                    // unconsumed so the surrounding scroll container remains fluid.
                                    break
                                }
                            }
                        }
                    } finally {
                        isDragging = false
                    }
                }
            }
            .testTag(testTag),
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val travelWidth = (totalWidth - thumbSize).coerceAtLeast(0.dp)
        val trackShape = NeumorphicShape.Pill
        val trackComposeShape = RoundedCornerShape(percent = 50)

        // Rail starts/ends under the thumb center, so the endpoints feel mechanically grounded.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = thumbSize / 2f)
                .height(trackHeight)
                .clip(trackComposeShape)
                .background(colors.surface)
                .neumorphicInset(colors = colors, shape = trackShape, depth = 3.dp)
        ) {
            // Active range: accent illumination diffused through the same surface material.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = normalizedValue)
                    .background(colors.surface)
                    .background(activeTrackBrush)
            )
        }

        // Raised puck: same scale family as the rail, with a buried indicator glow at its center.
        Box(
            modifier = Modifier
                .offset(x = travelWidth * normalizedValue)
                .size(thumbSize)
                .neumorphicRaised(
                    colors = colors,
                    shape = NeumorphicShape.Circle,
                    elevation = thumbElevation
                )
                .clip(CircleShape)
                .background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size((thumbSize * 0.48f).coerceAtLeast(7.dp))
                    .clip(CircleShape)
                    .background(thumbGlowBrush)
            )
        }
    }
}
