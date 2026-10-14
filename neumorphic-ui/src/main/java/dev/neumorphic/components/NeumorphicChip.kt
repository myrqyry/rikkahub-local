package dev.neumorphic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neumorphic.LocalNeumorphicColors
import dev.neumorphic.NeumorphicColors
import dev.neumorphic.NeumorphicShape
import dev.neumorphic.haptics.rememberNeumorphicHaptics
import dev.neumorphic.neumorphicRaised

/**
 * Reusable Neumorphic Tag / Chip matching image.png center widget (`[🎨 Neumorphic Design  ✕]`).
 */
@Composable
fun NeumorphicChip(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingIconTint: androidx.compose.ui.graphics.Color? = null,
    onCloseClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 12.dp,
    elevation: Dp = 3.dp,
    enabled: Boolean = true,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    testTag: String = "neumorphic_chip"
) {
    val haptics = rememberNeumorphicHaptics()
    val shape = NeumorphicShape.Rounded(cornerRadius)
    val composeShape = RoundedCornerShape(cornerRadius)

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                haptics.click()
                onClick()
            }
        )
    } else Modifier

    Box(
        modifier = modifier
            .neumorphicRaised(colors = colors, shape = shape, elevation = elevation)
            .clip(composeShape)
            .background(colors.surface)
            .then(clickModifier)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(testTag)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = leadingIconTint ?: colors.accentPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = text,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            if (onCloseClick != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptics.click()
                                onCloseClick()
                            }
                        ),
                    tint = colors.textSecondary.copy(alpha = 0.7f)
                )
            }
        }
    }
}
