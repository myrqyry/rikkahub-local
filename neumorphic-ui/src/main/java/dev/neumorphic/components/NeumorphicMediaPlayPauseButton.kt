package dev.neumorphic.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import dev.neumorphic.animation.rememberNeumorphicBreathingScale
import dev.neumorphic.haptics.rememberNeumorphicHaptics
import dev.neumorphic.neumorphicInset
import dev.neumorphic.neumorphicRaised

/**
 * Reusable Neumorphic Concentric Media Control Button with breathing outer ring, spring morphing, and tactile haptic pulse.
 */
@Composable
fun NeumorphicMediaPlayPauseButton(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 86.dp,
    enabled: Boolean = true,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    testTag: String = "neumorphic_media_button"
) {
    val haptics = rememberNeumorphicHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetElevation = if (isPressed) 1.dp else 4.dp
    val elevation by animateDpAsState(
        targetValue = targetElevation,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "mediaElev"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
        label = "mediaScale"
    )

    val breathingScale by rememberNeumorphicBreathingScale(minScale = 0.98f, maxScale = 1.03f)
    val outerRingScale = if (isPlaying) breathingScale else 1f

    val shape = NeumorphicShape.Circle

    // 1. Outer Concentric Raised Ring with animated breathing pulse when playing
    Box(
        modifier = modifier
            .scale(outerRingScale)
            .size(size)
            .neumorphicRaised(colors = colors, shape = shape, elevation = if (isPlaying) 6.dp else 4.dp)
            .clip(CircleShape)
            .background(colors.surface),
        contentAlignment = Alignment.Center
    ) {
        // 2. Middle Sunken Groove Ring
        Box(
            modifier = Modifier
                .size(size * 0.82f)
                .clip(CircleShape)
                .background(colors.surface)
                .neumorphicInset(colors = colors, shape = shape, depth = 2.5.dp),
            contentAlignment = Alignment.Center
        ) {
            // 3. Inner Interactive Raised Button
            val innerButtonModifier = if (isPressed) {
                Modifier
                    .size(size * 0.64f)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .neumorphicInset(colors = colors, shape = shape, depth = 3.dp)
            } else {
                Modifier
                    .size(size * 0.64f)
                    .scale(scale)
                    .neumorphicRaised(colors = colors, shape = shape, elevation = elevation)
                    .clip(CircleShape)
                    .background(colors.surface)
            }

            Box(
                modifier = innerButtonModifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            haptics.impact()
                            onToggle()
                        }
                    )
                    .alpha(if (enabled) 1f else 0.4f)
            .semantics { role = Role.Button }
            .testTag(testTag),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f)) + fadeIn()) togetherWith
                                (scaleOut(spring(dampingRatio = 0.8f, stiffness = 600f)) + fadeOut())
                    },
                    label = "playPauseIcon"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(size * 0.32f),
                        tint = colors.accentPrimary
                    )
                }
            }
        }
    }
}
