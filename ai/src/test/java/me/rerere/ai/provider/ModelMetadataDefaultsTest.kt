package me.rerere.ai.provider

import org.junit.Assert.*
import org.junit.Test

class ModelMetadataDefaultsTest {
    @Test fun catalogTemperatureSupportOverridesLegacyModelFamilyHeuristic() {
        assertFalse(Model(modelId = "gpt-5.5", supportsTemperature = false).allowsTemperature(legacyAllows = true))
        assertTrue(Model(modelId = "gpt-5-custom", supportsTemperature = true).allowsTemperature(legacyAllows = false))
        assertFalse(Model(modelId = "old-o1", supportsTemperature = null).allowsTemperature(legacyAllows = false))
        assertTrue(Model(modelId = "plain", supportsTemperature = null).allowsTemperature(legacyAllows = true))
    }

    @Test fun advertisedOutputLimitCapsExplicitTokensButPreservesUnknownLegacyBehavior() {
        val constrained = Model(modelId = "limited", maxOutputTokens = 8192)
        assertEquals(8192, constrained.clampRequestedMaxTokens(32000))
        assertEquals(4096, constrained.clampRequestedMaxTokens(4096))
        assertNull(constrained.clampRequestedMaxTokens(null))
        assertEquals(32000, Model(modelId = "legacy").clampRequestedMaxTokens(32000))
    }

    @Test fun absentMetadataKeepsOldModelStateOnJsonRoundTrip() {
        val old = Model(modelId = "legacy", displayName = "Manual")
        val serialized = kotlinx.serialization.json.Json.encodeToString(Model.serializer(), old)
        val roundTrip = kotlinx.serialization.json.Json.decodeFromString(Model.serializer(), serialized)
        assertEquals(old, roundTrip)
        assertNull(roundTrip.supportsTemperature)
        assertNull(roundTrip.maxOutputTokens)
    }
}
