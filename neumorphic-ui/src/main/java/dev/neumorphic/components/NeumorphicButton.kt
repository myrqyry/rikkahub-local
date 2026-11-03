package dev.neumorphic.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neumorphic.LocalNeumorphicColors
import dev.neumorphic.NeumorphicColors
import dev.neumorphic.NeumorphicShape
import dev.neumorphic.haptics.rememberNeumorphicHaptics
import dev.neumorphic.neumorphicInset
import dev.neumorphic.neumorphicRaised
import dev.neumorphic.toComposeShape

enum class NeumorphicButtonStyle {
    Soft,
    PrimaryGradient
}

/**
 * Reusable tactile Neumorphic Button with realistic spring press animation and haptic feedback.
 *
 * [cornerRadius] remains for source compatibility. Prefer [shape] for semantic geometry tokens.
 * [elevation] controls the resting raised depth and collapses toward 1.dp while pressed.
 */
@Composable
fun NeumorphicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NeumorphicButtonStyle = NeumorphicButtonStyle.Soft,
    cornerRadius: Dp = 14.dp,
    enabled: Boolean = true,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    testTag: String = "neumorphic_button",
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    text: String? = null,
    shape: NeumorphicShape?,
    elevation: Dp = 5.dp,
    content: (@Composable () -> Unit)? = null
) {
    val haptics = rememberNeumorphicHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetElevation = if (isPressed) 1.dp else elevation
    val animatedElevation by animateDpAsState(
        targetValue = targetElevation,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "btnElevation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "btnScale"
    )

    val resolvedShape = shape ?: NeumorphicShape.Rounded(cornerRadius)
    val composeShape = resolvedShape.toComposeShape()

    val backgroundModifier = when (style) {
        NeumorphicButtonStyle.PrimaryGradient -> {
            if (isPressed) {
                Modifier
                    .clip(composeShape)
                    .background(colors.accentGradient)
                    .neumorphicInset(colors = colors, shape = resolvedShape, depth = 3.dp)
            } else {
                Modifier
                    .neumorphicRaised(colors = colors, shape = resolvedShape, elevation = animatedElevation)
                    .clip(composeShape)
                    .background(colors.accentGradient)
            }
        }

        NeumorphicButtonStyle.Soft -> {
            if (isPressed) {
                Modifier
                    .clip(composeShape)
                    .background(colors.surface)
                    .neumorphicInset(colors = colors, shape = resolvedShape, depth = 3.dp)
            } else {
                Modifier
                    .neumorphicRaised(colors = colors, shape = resolvedShape, elevation = animatedElevation)
                    .clip(composeShape)
                    .background(colors.surface)
            }
        }
    }

    Box(
        modifier = modifier
            .scale(scale)
            .then(backgroundModifier)
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
            .defaultMinSize(minWidth = 110.dp, minHeight = 46.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .semantics { role = Role.Button }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) {
            content()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (text != null) {
                    val textColor = when (style) {
                        NeumorphicButtonStyle.PrimaryGradient -> colors.accentOnPrimary
                        NeumorphicButtonStyle.Soft -> colors.textPrimary
                    }
                    Text(
                        text = text,
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingIcon()
                }
            }
        }
    }
}

/**
 * Source- and binary-compatible overload for the pre-shape/elevation API.
 * Keeping this visible preserves positional final-content calls when clients recompile.
 */
@Composable
fun NeumorphicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NeumorphicButtonStyle = NeumorphicButtonStyle.Soft,
    cornerRadius: Dp = 14.dp,
    enabled: Boolean = true,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    testTag: String = "neumorphic_button",
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    text: String? = null,
    content: (@Composable () -> Unit)? = null
) = NeumorphicButton(
    onClick = onClick,
    modifier = modifier,
    style = style,
    cornerRadius = cornerRadius,
    enabled = enabled,
    colors = colors,
    testTag = testTag,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    text = text,
    shape = null,
    elevation = 5.dp,
    content = content
)
