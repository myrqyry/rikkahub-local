package dev.neumorphic.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.neumorphic.LocalNeumorphicColors
import dev.neumorphic.NeumorphicColors
import dev.neumorphic.NeumorphicShape
import dev.neumorphic.haptics.rememberNeumorphicHaptics
import dev.neumorphic.neumorphicInset
import dev.neumorphic.neumorphicRaised

/**
 * Reusable tactile switch with a pill rail and a surface-gliding thumb.
 *
 * The 48dp accessibility target deliberately lives outside the visual rail. This keeps compact
 * switch variants visually identical instead of letting [sizeIn] inflate the rail while the thumb
 * stays at its requested size.
 *
 * By default the thumb and rail share a height: the control reads as a puck travelling across a
 * tactile product surface rather than a small knob floating inside an oversized slot.
 */
@Composable
fun NeumorphicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    height: Dp = 34.dp,
    thumbSize: Dp = height,
    enabled: Boolean = true,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    testTag: String = "neumorphic_switch"
) {
    val haptics = rememberNeumorphicHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val maxThumbOffset = (width - thumbSize).coerceAtLeast(0.dp)
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) maxThumbOffset else 0.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "switchThumbOffset"
    )

    val thumbWidthScale by animateFloatAsState(
        targetValue = if (isPressed) 1.16f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "thumbStretch"
    )

    // Keep the emissive Brush stable; only the layer alpha animates. This avoids rebuilding a
    // gradient/list/color-stop graph on every spring frame.
    val emissionAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "switchEmissionAlpha"
    )
    val railEmissionBrush = remember(
        colors.accentPrimary,
        colors.accentGradientStart,
        colors.accentGradientEnd
    ) {
        Brush.horizontalGradient(
            listOf(
                colors.accentPrimary.copy(alpha = 0.36f),
                colors.accentGradientStart.copy(alpha = 0.62f),
                colors.accentGradientEnd.copy(alpha = 0.88f)
            )
        )
    }

    val inactiveThumbColor = remember(colors.textSecondary, colors.isDark) {
        colors.textSecondary.copy(alpha = if (colors.isDark) 0.88f else 0.74f)
    }
    val thumbColor by animateColorAsState(
        targetValue = if (checked) colors.accentOnPrimary else inactiveThumbColor,
        label = "switchThumbColor"
    )

    val trackShape = NeumorphicShape.Pill
    val composePill = RoundedCornerShape(percent = 50)

    // Outer box is only the touch/semantics target. Never paint the rail on this 48dp surface.
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .testTag(testTag)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { next ->
                    haptics.toggle(next)
                    onCheckedChange(next)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(width, height),
            contentAlignment = Alignment.CenterStart
        ) {
            // Physical rail shell. The child emission layer sits beneath the inset shadow so the
            // accent reads as light diffusing through the material, not paint on top of it.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(composePill)
                    .background(colors.surface)
                    .neumorphicInset(colors = colors, shape = trackShape, depth = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(composePill)
                        .graphicsLayer { alpha = emissionAlpha }
                        .background(railEmissionBrush)
                )
            }

            // Sliding tactile puck. It intentionally rides the rail at near-equal height.
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .graphicsLayer { scaleX = thumbWidthScale }
                    .size(thumbSize)
                    .neumorphicRaised(
                        colors = colors,
                        shape = NeumorphicShape.Circle,
                        elevation = 3.dp
                    )
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        }
    }
}
