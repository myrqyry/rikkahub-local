package dev.neumorphic

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.neumorphic.lighting.LightVector
import dev.neumorphic.lighting.LocalLightState
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Shadow engine for neumorphic depth.
 *
 * Raw calculations live here; surface primitives in [NeumorphicSurface] delegate here.
 * Blur/offset are density-aware (via Dp.toPx) and clamped to avoid clipping on very small or very large surfaces.
 * Components should prefer [NeumorphicElevation] and [NeumorphicShape] tokens over raw Dp.
 */

/**
 * Hoisted across draws: paints/rects are mutated per draw, never reallocated.
 * Mask filter is recreated only when blur changes (rare — elevation/size are static
 * for most surfaces). One filter instance is shared by both paints.
 */
// Plain mutable holders keep the draw path allocation-free without requiring a node refactor.
private class RaisedPaintCache {
    val light = android.graphics.Paint().apply { isAntiAlias = true }
    val dark = android.graphics.Paint().apply { isAntiAlias = true }
    val rectDark = android.graphics.RectF()
    val rectLight = android.graphics.RectF()
    var blurPx = -1f
}

/** Mutable anchor holder for the interaction-rate dynamic inset path. */
internal class InsetGeometryCache {
    var darkStartX = 0f
    var darkStartY = 0f
    var darkEndX = 0f
    var darkEndY = 0f
    var lightStartX = 0f
    var lightStartY = 0f
    var lightEndX = 0f
    var lightEndY = 0f

    fun update(
        width: Float,
        height: Float,
        spread: Float,
        direction: LightVector,
        shape: NeumorphicShape
    ) {
        val centerX = width / 2f
        val centerY = height / 2f
        val t = when (shape) {
            is NeumorphicShape.Circle -> {
                val radius = minOf(width, height) / 2f
                val magnitude = sqrt(direction.x * direction.x + direction.y * direction.y)
                if (magnitude > 1e-6f) radius / magnitude else 0f
            }
            is NeumorphicShape.Pill, is NeumorphicShape.Rounded -> {
                var edgeT = Float.POSITIVE_INFINITY
                val absX = abs(direction.x)
                val absY = abs(direction.y)
                if (absX > 1e-6f) edgeT = min(edgeT, centerX / absX)
                if (absY > 1e-6f) edgeT = min(edgeT, centerY / absY)
                if (edgeT.isFinite()) edgeT else 0f
            }
        }

        val facingX = centerX + direction.x * t
        val facingY = centerY + direction.y * t
        val oppositeX = centerX - direction.x * t
        val oppositeY = centerY - direction.y * t

        darkStartX = facingX
        darkStartY = facingY
        darkEndX = facingX - direction.x * spread
        darkEndY = facingY - direction.y * spread
        lightStartX = oppositeX
        lightStartY = oppositeY
        lightEndX = oppositeX + direction.x * spread
        lightEndY = oppositeY + direction.y * spread
    }
}


/**
 * Reusable native gradient/shader state for inset surfaces. The shaders are created only when
 * palette colors change; each draw mutates local matrices and geometry in place.
 */
private class InsetPaintCache(
    darkColors: List<Color>,
    lightColors: List<Color>
) {
    val darkPaint = androidx.compose.ui.graphics.Paint().apply { isAntiAlias = true }
    val lightPaint = androidx.compose.ui.graphics.Paint().apply { isAntiAlias = true }
    val clipPath = Path()

    private var pathWidth = Float.NaN
    private var pathHeight = Float.NaN
    private var pathShape: NeumorphicShape? = null
    private val darkMatrix = Matrix()
    private val lightMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val darkShader = LinearGradientShader(
        from = Offset.Zero,
        to = Offset(1f, 0f),
        colors = darkColors
    )
    private val lightShader = LinearGradientShader(
        from = Offset.Zero,
        to = Offset(1f, 0f),
        colors = lightColors
    )

    init {
        darkPaint.shader = darkShader
        lightPaint.shader = lightShader
    }

    fun ensurePath(
        width: Float,
        height: Float,
        shape: NeumorphicShape,
        cornerRadiusPx: Float
    ) {
        if (pathWidth == width && pathHeight == height && pathShape == shape) return
        pathWidth = width
        pathHeight = height
        pathShape = shape
        clipPath.reset()
        when (shape) {
            is NeumorphicShape.Circle -> {
                val radius = minOf(width, height) / 2f
                val centerX = width / 2f
                val centerY = height / 2f
                clipPath.addOval(
                    Rect(
                        centerX - radius,
                        centerY - radius,
                        centerX + radius,
                        centerY + radius
                    )
                )
            }
            is NeumorphicShape.Pill, is NeumorphicShape.Rounded -> {
                clipPath.addRoundRect(
                    RoundRect(
                        rect = Rect(0f, 0f, width, height),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )
                )
            }
        }
    }

    fun updateDark(startX: Float, startY: Float, endX: Float, endY: Float) {
        updateShader(darkShader, darkMatrix, startX, startY, endX, endY)
    }

    fun updateLight(startX: Float, startY: Float, endX: Float, endY: Float) {
        updateShader(lightShader, lightMatrix, startX, startY, endX, endY)
    }

    private fun updateShader(
        shader: Shader,
        matrix: Matrix,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ) {
        val dx = endX - startX
        val dy = endY - startY
        matrixValues[0] = dx
        matrixValues[1] = -dy
        matrixValues[2] = startX
        matrixValues[3] = dy
        matrixValues[4] = dx
        matrixValues[5] = startY
        matrixValues[6] = 0f
        matrixValues[7] = 0f
        matrixValues[8] = 1f
        matrix.setValues(matrixValues)
        shader.setLocalMatrix(matrix)
    }
}

/**
 * Applies a raised (convex/extruded) Neumorphic double-shadow effect.
 *
 * @param colors Neumorphic color palette
 * @param shape Geometry of the element (Rounded, Circle, Pill)
 * @param elevation Depth of the extrusion in Dp (affects offset and blur)
 *
 * Performance: paints, rects, and the clip path are hoisted across draws and mutated
 * per draw — no per-frame allocation. Animated depth changes recompose via elevation
 * param; keep animations spring-driven (see [dev.neumorphic.animation.NeumorphicMotion]).
 * @param intensity additional multiplier (prefer [NeumorphicElevation] tokens)
 * @param direction explicit light direction (direction-only, no dp/px). Null = read the
 * shared [dev.neumorphic.lighting.LocalLightState] holder in the draw phase; resolved
 * Static (or no holder) = today's code path, untouched.
 */
@Composable
internal fun Modifier.neumorphicRaisedInternal(
    colors: NeumorphicColors,
    shape: NeumorphicShape = NeumorphicShape.Medium,
    elevation: Dp,
    intensity: Float = 1.0f,
    direction: LightVector? = null
): Modifier {
    // Stable holder instance read at composition — never resubscribes.
    val lightHolder = LocalLightState.current
    // Native paints/rects persist across cache rebuilds; blur filters are replaced only
    // from drawWithCache when the computed blur changes, never from the draw callback.
    val paintCache = remember { RaisedPaintCache() }

    return this.drawWithCache {
        // Depth already clamped 0.5..2.0 in createNeumorphicPalette; clamp again for safety.
        val rawElevationPx = elevation.toPx() * intensity * colors.depthIntensityMultiplier
        val clampedElevationPx = rawElevationPx.coerceIn(0f, 24.dp.toPx().coerceAtLeast(1f))
        val blurPx = (clampedElevationPx * 1.5f).coerceIn(
            1f,
            (minOf(size.width, size.height) * 0.35f).coerceAtLeast(4f)
        )
        val legacyOffsetPx = (clampedElevationPx * 0.85f)
            .coerceAtMost(minOf(size.width, size.height) * 0.22f)

        paintCache.light.color = colors.lightShadow.copy(
            alpha = (colors.lightShadow.alpha * (if (colors.isDark) 0.85f else 0.95f))
                .coerceIn(0f, 1f)
        ).toArgb()
        paintCache.dark.color = colors.darkShadow.copy(
            alpha = (colors.darkShadow.alpha * (if (colors.isDark) 0.95f else 0.8f))
                .coerceIn(0f, 1f)
        ).toArgb()
        if (paintCache.blurPx != blurPx) {
            paintCache.blurPx = blurPx
            val filter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            paintCache.light.maskFilter = filter
            paintCache.dark.maskFilter = filter
        }

        onDrawBehind {
            // Reading snapshot here invalidates draw only for orientation/manual updates.
            val resolved: LightVector? = direction ?: lightHolder?.snapshot?.value
            val s: Offset = if (resolved == null || resolved == LightVector.StaticDefault) {
                // LEGACY PATH — pixel-identical to the static renderer.
                Offset(-legacyOffsetPx, -legacyOffsetPx)
            } else {
                val maxShiftPx = (direction?.let { 8.dp } ?: lightHolder?.maxShadowShift ?: 8.dp).toPx()
                val shiftPx = (legacyOffsetPx * 1.41421356f).coerceAtMost(maxShiftPx)
                Offset(resolved.x * shiftPx, resolved.y * shiftPx)
            }

            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                when (shape) {
                    is NeumorphicShape.Circle -> {
                        val radius = minOf(size.width, size.height) / 2f
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        nativeCanvas.drawCircle(
                            centerX - s.x,
                            centerY - s.y,
                            radius,
                            paintCache.dark
                        )
                        nativeCanvas.drawCircle(
                            centerX + s.x,
                            centerY + s.y,
                            radius,
                            paintCache.light
                        )
                    }
                    is NeumorphicShape.Pill -> {
                        val cornerRadiusPx = size.height / 2f
                        paintCache.rectDark.set(
                            -s.x,
                            -s.y,
                            size.width - s.x,
                            size.height - s.y
                        )
                        paintCache.rectLight.set(
                            s.x,
                            s.y,
                            size.width + s.x,
                            size.height + s.y
                        )
                        nativeCanvas.drawRoundRect(
                            paintCache.rectDark,
                            cornerRadiusPx,
                            cornerRadiusPx,
                            paintCache.dark
                        )
                        nativeCanvas.drawRoundRect(
                            paintCache.rectLight,
                            cornerRadiusPx,
                            cornerRadiusPx,
                            paintCache.light
                        )
                    }
                    is NeumorphicShape.Rounded -> {
                        val cornerRadiusPx = shape.cornerRadius.toPx()
                        paintCache.rectDark.set(
                            -s.x,
                            -s.y,
                            size.width - s.x,
                            size.height - s.y
                        )
                        paintCache.rectLight.set(
                            s.x,
                            s.y,
                            size.width + s.x,
                            size.height + s.y
                        )
                        nativeCanvas.drawRoundRect(
                            paintCache.rectDark,
                            cornerRadiusPx,
                            cornerRadiusPx,
                            paintCache.dark
                        )
                        nativeCanvas.drawRoundRect(
                            paintCache.rectLight,
                            cornerRadiusPx,
                            cornerRadiusPx,
                            paintCache.light
                        )
                    }
                }
            }
        }
    }
}

/**
 * Applies a sunken / concave Neumorphic inner-shadow well effect.
 * Perfect for search bars, switch tracks, sliders, unselected radio/check slots, and pressed states.
 *
 * @param direction explicit light direction (direction-only, no dp/px). Null = read the
 * shared [dev.neumorphic.lighting.LocalLightState] holder in the draw phase; resolved
 * Static (or no holder) = today's code path, untouched. Inversion lives entirely here;
 * call sites never change.
 */
@Composable
internal fun Modifier.neumorphicInsetInternal(
    colors: NeumorphicColors,
    shape: NeumorphicShape = NeumorphicShape.Medium,
    depth: Dp,
    intensity: Float = 1.0f,
    direction: LightVector? = null
): Modifier {
    val lightHolder = LocalLightState.current
    val geometryCache = remember { InsetGeometryCache() }
    val darkGradientColors = remember(colors.innerDarkShadow) {
        listOf(
            colors.innerDarkShadow,
            colors.innerDarkShadow.copy(alpha = colors.innerDarkShadow.alpha * 0.4f),
            Color.Transparent
        )
    }
    val lightGradientColors = remember(colors.innerLightShadow) {
        listOf(
            colors.innerLightShadow,
            colors.innerLightShadow.copy(alpha = colors.innerLightShadow.alpha * 0.3f),
            Color.Transparent
        )
    }
    val paintCache = remember(darkGradientColors, lightGradientColors) {
        InsetPaintCache(darkGradientColors, lightGradientColors)
    }

    return this.drawWithCache {
        val rawDepthPx = depth.toPx() * intensity * colors.depthIntensityMultiplier
        val depthPx = rawDepthPx.coerceIn(0f, 20.dp.toPx().coerceAtLeast(1f))
        val strokeWidthPx = (depthPx * 2.2f).coerceIn(
            2f,
            (minOf(size.width, size.height) * 0.45f).coerceAtLeast(2f)
        )
        val cornerRadiusPx = when (shape) {
            is NeumorphicShape.Rounded -> shape.cornerRadius.toPx()
            is NeumorphicShape.Pill -> size.height / 2f
            is NeumorphicShape.Circle -> minOf(size.width, size.height) / 2f
        }
        val spread = strokeWidthPx * 1.5f

        // Cache the exact legacy brushes for the static path. They are rebuilt only when
        // size/depth/palette changes, never for sensor-driven redraws. This preserves the
        // original Compose shader output pixel-for-pixel while keeping the hot draw path clean.
        val legacyDarkBrush = Brush.linearGradient(
            colors = darkGradientColors,
            start = Offset.Zero,
            end = Offset(spread, spread)
        )
        val legacyLightBrush = Brush.linearGradient(
            colors = lightGradientColors,
            start = Offset(size.width, size.height),
            end = Offset(size.width - spread, size.height - spread)
        )

        onDrawWithContent {
            drawContent()

            val resolved: LightVector? = direction ?: lightHolder?.snapshot?.value
            val dynamicDirection = if (resolved != null && resolved != LightVector.StaticDefault) {
                resolved
            } else {
                null
            }
            if (dynamicDirection != null) {
                geometryCache.update(
                    width = size.width,
                    height = size.height,
                    spread = spread * 1.41421356f,
                    direction = dynamicDirection,
                    shape = shape
                )
                paintCache.updateDark(
                    geometryCache.darkStartX, geometryCache.darkStartY,
                    geometryCache.darkEndX, geometryCache.darkEndY
                )
                paintCache.updateLight(
                    geometryCache.lightStartX, geometryCache.lightStartY,
                    geometryCache.lightEndX, geometryCache.lightEndY
                )
            }

            drawIntoCanvas { canvas ->
                val centerX = size.width / 2f
                val centerY = size.height / 2f

                paintCache.ensurePath(size.width, size.height, shape, cornerRadiusPx)
                canvas.save()
                canvas.clipPath(paintCache.clipPath)

                if (dynamicDirection == null) {
                    // Exact legacy rendering, with brushes cached by drawWithCache.
                    drawRoundRect(
                        brush = legacyDarkBrush,
                        size = size,
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )
                    drawRoundRect(
                        brush = legacyLightBrush,
                        size = size,
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )
                } else {
                    when (shape) {
                        is NeumorphicShape.Circle -> {
                            val radius = minOf(size.width, size.height) / 2f
                            val center = Offset(centerX, centerY)
                            canvas.drawCircle(center, radius, paintCache.darkPaint)
                            canvas.drawCircle(center, radius, paintCache.lightPaint)
                        }
                        is NeumorphicShape.Pill, is NeumorphicShape.Rounded -> {
                            canvas.drawRoundRect(
                                0f, 0f, size.width, size.height,
                                cornerRadiusPx, cornerRadiusPx, paintCache.darkPaint
                            )
                            canvas.drawRoundRect(
                                0f, 0f, size.width, size.height,
                                cornerRadiusPx, cornerRadiusPx, paintCache.lightPaint
                            )
                        }
                    }
                }
                canvas.restore()
            }
        }
    }
}

/**
 * Semantic overload for [neumorphicRaised] using [NeumorphicElevation].
 *
 * Kept non-composable so reusable Modifier factories compiled against earlier releases remain valid.
 * The shared light holder is resolved when Compose materializes this modifier.
 */
fun Modifier.neumorphicRaised(
    colors: NeumorphicColors,
    shape: NeumorphicShape = NeumorphicShape.Medium,
    elevation: NeumorphicElevation = NeumorphicElevation.Normal,
    intensity: Float = 1.0f
): Modifier = composed {
    neumorphicRaisedInternal(colors, shape, elevation.dp, intensity)
}

/**
 * Raw Dp overload for advanced cases — prefer the semantic [NeumorphicElevation] overload.
 * No default on `elevation` to avoid overload ambiguity with the semantic version.
 */
fun Modifier.neumorphicRaised(
    colors: NeumorphicColors,
    shape: NeumorphicShape = NeumorphicShape.Medium,
    elevation: Dp,
    intensity: Float = 1.0f
): Modifier = composed {
    neumorphicRaisedInternal(colors, shape, elevation, intensity)
}

/** Semantic overload for [neumorphicInset] using [NeumorphicElevation]. */
fun Modifier.neumorphicInset(
    colors: NeumorphicColors,
    shape: NeumorphicShape = NeumorphicShape.Medium,
    elevation: NeumorphicElevation = NeumorphicElevation.Normal,
    intensity: Float = 1.0f
): Modifier = composed {
    neumorphicInsetInternal(colors, shape, elevation.dp, intensity)
}

/** Raw Dp overload for inset — no default to avoid ambiguity. */
fun Modifier.neumorphicInset(
    colors: NeumorphicColors,
    shape: NeumorphicShape = NeumorphicShape.Medium,
    depth: Dp,
    intensity: Float = 1.0f
): Modifier = composed {
    neumorphicInsetInternal(colors, shape, depth, intensity)
}
