package dev.neumorphic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.neumorphic.LocalNeumorphicColors
import dev.neumorphic.NeumorphicColors
import dev.neumorphic.NeumorphicShape
import dev.neumorphic.neumorphicInset
import dev.neumorphic.neumorphicRaised
import dev.neumorphic.toComposeShape

/**
 * Reusable Neumorphic Card container. Supports Raised (extruded) or Inset (sunken) styles.
 *
 * [cornerRadius] remains for source compatibility. Prefer [shape] for semantic geometry tokens.
 */
@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    cornerRadius: Dp = 20.dp,
    elevation: Dp = 6.dp,
    isInset: Boolean = false,
    contentPadding: Dp = 16.dp,
    backgroundColor: Color = colors.surface,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    shape: NeumorphicShape?,
    content: @Composable BoxScope.() -> Unit
) {
    val resolvedShape = shape ?: NeumorphicShape.Rounded(cornerRadius)
    val composeShape = resolvedShape.toComposeShape()

    val shadowModifier = if (isInset) {
        Modifier
            .clip(composeShape)
            .background(backgroundColor)
            .neumorphicInset(colors = colors, shape = resolvedShape, depth = elevation)
    } else {
        Modifier
            .neumorphicRaised(colors = colors, shape = resolvedShape, elevation = elevation)
            .clip(composeShape)
            .background(backgroundColor)
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(shadowModifier)
            .then(if (enabled) Modifier else Modifier.alpha(0.4f))
            .then(if (onClick != null) Modifier.semantics { role = Role.Button } else Modifier)
            .then(clickModifier)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Source- and binary-compatible overload for the pre-shape API.
 * Keeping this visible preserves positional final-content calls when clients recompile.
 */
@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    cornerRadius: Dp = 20.dp,
    elevation: Dp = 6.dp,
    isInset: Boolean = false,
    contentPadding: Dp = 16.dp,
    backgroundColor: Color = colors.surface,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) = NeumorphicCard(
    modifier = modifier,
    colors = colors,
    cornerRadius = cornerRadius,
    elevation = elevation,
    isInset = isInset,
    contentPadding = contentPadding,
    backgroundColor = backgroundColor,
    enabled = enabled,
    onClick = onClick,
    shape = null,
    content = content
)
