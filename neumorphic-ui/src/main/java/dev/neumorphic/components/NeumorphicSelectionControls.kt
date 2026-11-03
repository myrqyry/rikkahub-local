package dev.neumorphic.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neumorphic.LocalNeumorphicColors
import dev.neumorphic.NeumorphicColors
import dev.neumorphic.NeumorphicShape
import dev.neumorphic.haptics.rememberNeumorphicHaptics
import dev.neumorphic.neumorphicInset
import dev.neumorphic.neumorphicRaised

/**
 * Reusable Neumorphic Checkbox matching image.png with smooth check animation and sunken/raised transitions.
 */
@Composable
fun NeumorphicCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    size: Dp = 32.dp,
    cornerRadius: Dp = 10.dp,
    enabled: Boolean = true,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    testTag: String = "neumorphic_checkbox"
) {
    val haptics = rememberNeumorphicHaptics()
    val shape = NeumorphicShape.Rounded(cornerRadius)
    val composeShape = RoundedCornerShape(cornerRadius)

    val shadowModifier = if (checked) {
        Modifier
            .neumorphicRaised(colors = colors, shape = shape, elevation = 2.dp)
            .clip(composeShape)
            .background(colors.surface)
    } else {
        Modifier
            .clip(composeShape)
            .background(colors.surface)
            .neumorphicInset(colors = colors, shape = shape, depth = 3.dp)
    }

    Row(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptics.toggle(!checked)
                    onCheckedChange(!checked)
                }
            )
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .then(shadowModifier),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = checked,
                enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Checked",
                    modifier = Modifier.size(size * 0.6f),
                    tint = colors.accentPrimary
                )
            }
        }

        if (label != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Reusable Neumorphic Radio Button matching image.png with smooth glowing center dot.
 */
@Composable
fun NeumorphicRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    size: Dp = 32.dp,
    enabled: Boolean = true,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    testTag: String = "neumorphic_radio_button"
) {
    val haptics = rememberNeumorphicHaptics()
    val shape = NeumorphicShape.Circle

    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "radioDotScale"
    )

    Row(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!selected) {
                        haptics.select()
                    }
                    onClick()
                }
            )
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.surface)
                .neumorphicInset(colors = colors, shape = shape, depth = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            if (dotScale > 0f) {
                Box(
                    modifier = Modifier
                        .size(size * 0.45f)
                        .scale(dotScale)
                        .clip(CircleShape)
                        .background(colors.accentPrimary)
                        .neumorphicRaised(colors = colors, shape = shape, elevation = 2.dp)
                )
            }
        }

        if (label != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 14.sp
            )
        }
    }
}
