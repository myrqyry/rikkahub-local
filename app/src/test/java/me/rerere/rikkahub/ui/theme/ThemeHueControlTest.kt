package me.rerere.rikkahub.ui.theme

import hct.Hct
import me.rerere.rikkahub.ui.pages.setting.hueOf
import me.rerere.rikkahub.ui.pages.setting.withHue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ThemeHueControlTest {
    @Test fun hueRotationRetainsOpacityAndApproximateTone() {
        val source = 0xFF44A3EAL
        val original = Hct.fromInt(source.toInt())
        val rotated = withHue(source, 45f)
        val adjusted = Hct.fromInt(rotated.toInt())
        assertEquals(0xFFL, (rotated shr 24) and 0xFFL)
        assertTrue(abs(adjusted.hue - original.hue) > 20)
        assertTrue(abs(adjusted.tone - original.tone) < 3)
        assertTrue(adjusted.chroma > 10)
    }

    @Test fun changingHueTwiceDoesNotAccumulateToneDrift() {
        val source = 0xFF7DA8C8L
        val changed = withHue(source, 145f)
        val changedBack = withHue(source, hueOf(source))
        val initial = Hct.fromInt(source.toInt())
        assertTrue(abs(Hct.fromInt(changed.toInt()).hue - initial.hue) > 20)
        assertTrue(abs(Hct.fromInt(changedBack.toInt()).tone - initial.tone) < 2)
    }
}
