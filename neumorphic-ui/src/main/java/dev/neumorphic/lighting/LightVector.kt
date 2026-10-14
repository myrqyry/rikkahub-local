package dev.neumorphic.lighting

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Direction toward the apparent light source. Direction only — carries no dp/px. */
@Immutable
data class LightVector(val x: Float, val y: Float) {
    companion object {
        /** Normalized (-1,-1): reproduces today's rendering exactly. */
        val StaticDefault = LightVector(-0.70710678f, -0.70710678f)
    }
}

/** The single place vectors become unit length. Zero vector falls back to Static. */
fun LightVector.unit(): LightVector {
    val len = sqrt(x * x + y * y)
    if (len < 1e-6f) return LightVector.StaticDefault
    if (abs(len - 1f) < 1e-6f) return this
    return LightVector(x / len, y / len)
}

/**
 * Angle interpolation between unit vectors. Intensity t: 0 -> from (Static),
 * 1 -> to (target). Slerp, not XY lerp: opposite vectors must not collapse to (0,0).
 */
fun angularBlend(from: LightVector, to: LightVector, t: Float): LightVector {
    val ct = t.coerceIn(0f, 1f)
    if (ct <= 0f) return from
    if (ct >= 1f) return to
    val angle = acos((from.x * to.x + from.y * to.y).coerceIn(-1f, 1f))
    if (angle < 1e-4f) return to
    val sinA = sin(angle)
    if (sinA < 1e-3f) {
        // Antiparallel: slerp is degenerate (sin π ≈ 0, midpoint collapses to
        // the zero vector). Preserve the signed side of the target so sensor noise
        // crossing the antipode cannot make the light jump to the opposite arc.
        val cross = from.x * to.y - from.y * to.x
        val direction = if (cross < 0f) -1f else 1f
        val theta = ct * angle * direction
        val c = cos(theta)
        val s = sin(theta)
        return LightVector(from.x * c - from.y * s, from.x * s + from.y * c)
    }
    val a = sin((1f - ct) * angle) / sinA
    val b = sin(ct * angle) / sinA
    return LightVector(a * from.x + b * to.x, a * from.y + b * to.y)
}

/**
 * Sensor pipeline output: screen-space tilt (pitch/roll radians) -> target vector.
 * Deadband applies BEFORE normalization to the raw tilt delta; neutral tilt yields
 * exactly Static. Convention: sun swings opposite the raised edge (world-anchored
 * feel). Sign verified on-device in Task 10 — if inverted, flip here + update tests.
 */
fun tiltTarget(pitchRad: Float, rollRad: Float, deadband: Float = 0.05f): LightVector {
    val raw = Offset(-rollRad, -pitchRad)
    if (raw.getDistance() < deadband) return LightVector.StaticDefault
    return LightVector(raw.x, raw.y).unit()
}

/** Ray from rect center along unit dir to the boundary. Hits edges on non-square rects. */
fun rayBoundaryPoint(size: Size, dir: Offset): Offset {
    var t = Float.POSITIVE_INFINITY
    if (abs(dir.x) > 1e-6f) t = min(t, (size.width / 2f) / abs(dir.x))
    if (abs(dir.y) > 1e-6f) t = min(t, (size.height / 2f) / abs(dir.y))
    return Offset(size.width / 2f + dir.x * t, size.height / 2f + dir.y * t)
}

data class InsetAnchors(
    val darkStart: Offset, val darkEnd: Offset,
    val lightStart: Offset, val lightEnd: Offset
)

/**
 * Inset gradient anchors for an arbitrary light direction. Dark inner edge faces
 * the incoming light. `spread` is the inward gradient distance ALONG the light
 * direction; pass `strokeWidthPx * 1.5f * sqrt(2)` to match legacy coordinates
 * at the default diagonal (proven by Task 1's legacy test).
 */
fun insetAnchors(size: Size, spread: Float, dir: LightVector): InsetAnchors {
    val d = Offset(dir.x, dir.y)
    val facing = rayBoundaryPoint(size, d)
    val opposite = rayBoundaryPoint(size, -d)
    return InsetAnchors(
        darkStart = facing, darkEnd = facing - d * spread,
        lightStart = opposite, lightEnd = opposite + d * spread
    )
}
