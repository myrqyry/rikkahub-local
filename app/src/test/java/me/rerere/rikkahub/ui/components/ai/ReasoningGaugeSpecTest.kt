package me.rerere.rikkahub.ui.components.ai

import me.rerere.ai.core.ReasoningLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningGaugeSpecTest {
    @Test fun fourSourceGaugeNeedlesMapToActualEffortStates() {
        assertEquals(15f, ReasoningLevel.OFF.gaugeNeedleDegrees())
        assertEquals(65f, ReasoningLevel.LOW.gaugeNeedleDegrees())
        assertEquals(115f, ReasoningLevel.MEDIUM.gaugeNeedleDegrees())
        assertEquals(165f, ReasoningLevel.HIGH.gaugeNeedleDegrees())
    }

    @Test fun autoNeverMasqueradesAsAChosenEffort() {
        assertNull(ReasoningLevel.AUTO.gaugeNeedleDegrees())
        assertTrue(ReasoningLevel.AUTO.gaugeShowsAutoMarker())
        assertFalse(ReasoningLevel.OFF.gaugeShowsAutoMarker())
    }

    @Test fun extraHighUsesFullScaleNeedleWithSeparateMarker() {
        assertEquals(165f, ReasoningLevel.XHIGH.gaugeNeedleDegrees())
        assertTrue(ReasoningLevel.XHIGH.gaugeShowsExtraHighMarker())
        assertFalse(ReasoningLevel.HIGH.gaugeShowsExtraHighMarker())
    }

    @Test fun needleAnglesMatchAllFourOriginalLineMdGaugeVariants() {
        val angles = listOf(ReasoningLevel.OFF, ReasoningLevel.LOW,
            ReasoningLevel.MEDIUM, ReasoningLevel.HIGH).map {
            it.gaugeNeedleDegrees()
        }
        assertEquals(4, angles.distinct().size)
        assertTrue(angles.all { it != null && it >= 15f && it <= 165f })
    }
}
