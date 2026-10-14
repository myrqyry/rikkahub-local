package dev.neumorphic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Raised surface primitive — the single place raised neumorphic depth is applied.
 *
 * Shadow engine → this primitive → buttons/cards/controls.
 * Prefer this over calling [neumorphicRaised] directly in components; it encodes
 * the correct clip/background/elevation ordering and semantic shape handling
 * (Rounded/Circle/Pill) so every raised component clips the same way.
 *
 * @param shape geometry; prefer [NeumorphicShape.Small]/[NeumorphicShape.Medium]/[NeumorphicShape.Large]/[NeumorphicShape.PillShape]/[NeumorphicShape.Circular].
 * @param elevation depth level; prefer [NeumorphicElevation] overload when possible.
 */
/**
 * Internal raw-Dp implementation — use the semantic [NeumorphicElevation] overload in public API.
 */
@Composable
internal fun NeumorphicRaisedSurfaceWithDp(
    modifier: Modifier = Modifier,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    shape: NeumorphicShape = NeumorphicShape.Medium,
    elevation: Dp,
    backgroundColor: Color = colors.surface,
    content: @Composable BoxScope.() -> Unit
) {
    val composeShape = shape.toComposeShape()
    Box(
        modifier = modifier
            .neumorphicRaised(colors = colors, shape = shape, elevation = elevation)
            .clip(composeShape)
            .background(backgroundColor),
        content = content
    )
}

@Composable
fun NeumorphicRaisedSurface(
    modifier: Modifier = Modifier,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    shape: NeumorphicShape = NeumorphicShape.Medium,
    elevation: NeumorphicElevation = NeumorphicElevation.Normal,
    backgroundColor: Color = colors.surface,
    content: @Composable BoxScope.() -> Unit
) = NeumorphicRaisedSurfaceWithDp(modifier, colors, shape, elevation.dp, backgroundColor, content)

/**
 * Inset (sunken) surface primitive — wells, tracks, pressed states.
 * Mirrors [NeumorphicRaisedSurface] but uses [neumorphicInset] with correct
 * clip-then-draw ordering for inner shadows.
 */
@Composable
internal fun NeumorphicInsetSurfaceWithDp(
    modifier: Modifier = Modifier,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    shape: NeumorphicShape = NeumorphicShape.Medium,
    elevation: Dp,
    backgroundColor: Color = colors.surface,
    content: @Composable BoxScope.() -> Unit
) {
    val composeShape = shape.toComposeShape()
    Box(
        modifier = modifier
            .clip(composeShape)
            .background(backgroundColor)
            .neumorphicInset(colors = colors, shape = shape, depth = elevation),
        content = content
    )
}

@Composable
fun NeumorphicInsetSurface(
    modifier: Modifier = Modifier,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    shape: NeumorphicShape = NeumorphicShape.Medium,
    elevation: NeumorphicElevation = NeumorphicElevation.Normal,
    backgroundColor: Color = colors.surface,
    content: @Composable BoxScope.() -> Unit
) = NeumorphicInsetSurfaceWithDp(modifier, colors, shape, elevation.dp, backgroundColor, content)

/**
 * Maps a semantic [NeumorphicShape] token to its Compose [Shape].
 *
 * Public so demos and custom components can clip ancillary content using the
 * exact same geometry as the neumorphic surface primitives.
 */
private object InscribedCircleShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val diameter = minOf(size.width, size.height)
        val left = (size.width - diameter) / 2f
        val top = (size.height - diameter) / 2f
        val radius = diameter / 2f
        return Outline.Rounded(
            RoundRect(
                rect = Rect(left, top, left + diameter, top + diameter),
                cornerRadius = CornerRadius(radius, radius)
            )
        )
    }
}

fun NeumorphicShape.toComposeShape(): Shape = when (this) {
    is NeumorphicShape.Circle -> InscribedCircleShape
    is NeumorphicShape.Pill -> RoundedCornerShape(percent = 50)
    is NeumorphicShape.Rounded -> RoundedCornerShape(cornerRadius)
}
