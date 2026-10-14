package dev.neumorphic.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.neumorphic.LocalNeumorphicColors
import dev.neumorphic.NeumorphicColors
import dev.neumorphic.NeumorphicShape
import dev.neumorphic.haptics.rememberNeumorphicHaptics
import dev.neumorphic.neumorphicInset
import dev.neumorphic.neumorphicRaised

/**
 * Reusable Circular Neumorphic Icon Button matching the style in the prompt image (Home, User, Settings).
 */
@Composable
fun NeumorphicIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    isActive: Boolean = false,
    enabled: Boolean = true,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    testTag: String = "neumorphic_icon_button"
) {
    val haptics = rememberNeumorphicHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetElevation = if (isPressed || isActive) 1.dp else 4.dp
    val elevation by animateDpAsState(
        targetValue = targetElevation,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "iconBtnElevation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "iconBtnScale"
    )

    val shape = NeumorphicShape.Circle
    val isInsetMode = isPressed || isActive

    val shadowModifier = if (isActive) {
        Modifier
            .clip(CircleShape)
            .background(colors.accentPrimary)
            .neumorphicRaised(colors = colors, shape = shape, elevation = 2.dp)
    } else if (isPressed) {
        Modifier
            .clip(CircleShape)
            .background(colors.surface)
            .neumorphicInset(colors = colors, shape = shape, depth = 3.dp)
    } else {
        Modifier
            .neumorphicRaised(colors = colors, shape = shape, elevation = elevation)
            .clip(CircleShape)
            .background(colors.surface)
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .then(shadowModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.click()
                    onClick()
                }
            )
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { role = Role.Button }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        val iconTint = if (isActive) colors.accentOnPrimary else colors.textSecondary
        if (isActive) {
            // ponytail: steady diffused halo under the glyph; pulse helper if this ever needs to breathe
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        // Keep the halo within the clipped button so the fade never chops at the edge.
                        .size((iconSize * 2.4f).coerceAtMost(size - 2.dp))
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.accentOnPrimary.copy(alpha = 0.85f),
                                    colors.accentOnPrimary.copy(alpha = 0f)
                                )
                            )
                        )
                )
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(iconSize),
                    tint = iconTint
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = iconTint
            )
        }
    }
}
