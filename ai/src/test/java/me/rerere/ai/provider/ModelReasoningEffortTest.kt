package me.rerere.ai.provider

import me.rerere.ai.core.ReasoningLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelReasoningEffortTest {
    @Test fun unsupportedXhighFallsBackToHighestAdvertisedEffortWithoutUpshifting() {
        val model = Model(modelId = "reasoning", reasoningEfforts = listOf("low", "medium", "high"))
        assertEquals("high", model.supportedReasoningEffort(ReasoningLevel.XHIGH))
        assertEquals("medium", model.supportedReasoningEffort(ReasoningLevel.MEDIUM))
    }

    @Test fun unsupportedMediumUsesLowerEffortNotAnUnsupportedString() {
        val model = Model(reasoningEfforts = listOf("low", "high"))
        assertEquals("low", model.supportedReasoningEffort(ReasoningLevel.MEDIUM))
    }

    @Test fun doesNotUpshiftLowToHighWhenNoLowerAdvertisedEffortExists() {
        val model = Model(reasoningEfforts = listOf("high"))
        assertNull(model.supportedReasoningEffort(ReasoningLevel.LOW))
    }

    @Test fun acceptsProvidersMaxEffortAsXhighEquivalentWhenXhighRequested() {
        val model = Model(reasoningEfforts = listOf("low", "high", "max"))
        assertEquals("max", model.supportedReasoningEffort(ReasoningLevel.XHIGH))
        assertEquals("high", model.supportedReasoningEffort(ReasoningLevel.HIGH))
    }

    @Test fun unknownCatalogEffortsAvoidInventingAParameterValue() {
        val model = Model(reasoningEfforts = listOf("provider-private-mode"))
        assertNull(model.supportedReasoningEffort(ReasoningLevel.HIGH))
    }

    @Test fun missingCatalogMetadataPreservesLegacyEffortAndAutoOffAreNotConverted() {
        val model = Model()
        assertEquals("xhigh", model.supportedReasoningEffort(ReasoningLevel.XHIGH))
        assertNull(model.supportedReasoningEffort(ReasoningLevel.AUTO))
        assertNull(model.supportedReasoningEffort(ReasoningLevel.OFF))
    }

    @Test fun minimalEffortIsUsedOnlyForPositiveReasoningWithoutACloserSupportedOption() {
        val model = Model(reasoningEfforts = listOf("minimal", "high"))
        assertEquals("minimal", model.supportedReasoningEffort(ReasoningLevel.LOW))
        assertNull(model.supportedReasoningEffort(ReasoningLevel.OFF))
    }
}
