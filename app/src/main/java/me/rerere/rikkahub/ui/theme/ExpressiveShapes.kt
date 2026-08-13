package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * A hand-drawn squiggle container outline (M3 Expressive "squiggle" look).
 * Every edge is perturbed with a small quadratic wave. Use on decorative
 * surfaces with some internal padding so the wave never clips content.
 *
 * ponytail: material3 1.5.0-alpha23 ships no SquiggleShape/CornerStyle API,
 * so we hand-roll the outline. Rework with the real API when it lands.
 */
class SquiggleShape(
    private val amplitude: Float = 3f,
    private val wavelength: Float = 24f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        val amp = amplitude * 2f

        path.moveTo(0f, 0f)
        var x = 0f
        while (x < w) {
            val seg = min(wavelength, w - x)
            path.quadraticTo(x + seg * 0.5f, -amp, x + seg, 0f)
            x += seg
        }
        path.lineTo(w, 0f)
        var y = 0f
        while (y < h) {
            val seg = min(wavelength, h - y)
            path.quadraticTo(w + amp, y + seg * 0.5f, w, y + seg)
            y += seg
        }
        path.lineTo(w, h)
        x = w
        while (x > 0f) {
            val seg = min(wavelength, x)
            path.quadraticTo(x - seg * 0.5f, h + amp, x - seg, h)
            x -= seg
        }
        path.lineTo(0f, h)
        y = h
        while (y > 0f) {
            val seg = min(wavelength, y)
            path.quadraticTo(-amp, y - seg * 0.5f, 0f, y - seg)
            y -= seg
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * A container with a wavy bottom edge (flat otherwise) — good for sheets,
 * cards and panels where a playful baseline reads well.
 */
class WaveShape(
    private val amplitude: Float = 6f,
    private val wavelength: Float = 32f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        val w = size.width
        val h = size.height

        path.moveTo(0f, 0f)
        path.lineTo(w, 0f)
        path.lineTo(w, h - amplitude)
        var x = w
        while (x > 0f) {
            val seg = min(wavelength, x)
            path.quadraticTo(x - seg * 0.5f, h + amplitude, x - seg, h - amplitude)
            x -= seg
        }
        path.lineTo(0f, 0f)
        path.close()
        return Outline.Generic(path)
    }
}
