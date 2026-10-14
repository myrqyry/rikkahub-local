package dev.neumorphic.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.neumorphic.LocalNeumorphicColors
import dev.neumorphic.NeumorphicColors
import dev.neumorphic.NeumorphicShape
import dev.neumorphic.neumorphicInset

/**
 * A small status emitter that looks lit from beneath the surface rather than painted on top.
 *
 * The shell remains the normal neumorphic surface in both states. When active, a radial accent
 * emission blooms through it with a soft falloff, inspired by translucent consumer-electronics
 * plastics with an LED mounted below the outer material.
 */
@Composable
fun NeumorphicStatusLight(
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    colors: NeumorphicColors = LocalNeumorphicColors.current
) {
    // Animate only a layer alpha. The radial Brush and its stop list stay stable across animation
    // frames and are recreated only when the active accent changes.
    val emissionAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "statusLightEmissionAlpha"
    )
    val emissionBrush = remember(colors.accentPrimary) {
        Brush.radialGradient(
            colors = listOf(
                colors.accentPrimary.copy(alpha = 0.88f),
                colors.accentPrimary.copy(alpha = 0.60f),
                colors.accentPrimary.copy(alpha = 0.26f),
                Color.Transparent
            )
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surface)
            .neumorphicInset(
                colors = colors,
                shape = NeumorphicShape.Circle,
                depth = 1.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .graphicsLayer { alpha = emissionAlpha }
                .background(emissionBrush)
        )
    }
}
