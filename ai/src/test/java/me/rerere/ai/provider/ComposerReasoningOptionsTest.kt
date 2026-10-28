package me.rerere.ai.provider

import me.rerere.ai.core.ReasoningLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerReasoningOptionsTest {
    private fun reasoningModel(
        efforts: List<String> = emptyList(),
        controls: List<String> = emptyList(),
    ) = Model(
        modelId = "reasoning-model",
        abilities = listOf(ModelAbility.REASONING),
        reasoningEfforts = efforts,
        reasoningControlTypes = controls,
    )

    @Test fun nonReasoningModelHasNoComposerControlEvenIfMetadataClaimsEfforts() {
        val model = Model(reasoningEfforts = listOf("low", "high"), reasoningControlTypes = listOf("effort"))
        assertTrue(model.composerReasoningLevels().isEmpty())
        assertEquals(ReasoningLevel.OFF, model.effectiveComposerReasoningLevel(ReasoningLevel.HIGH))
    }

    @Test fun explicitEffortsOfferOnlySupportedLevelsPlusAutomatic() {
        val model = reasoningModel(listOf("low", "high"), listOf("effort"))
        assertEquals(
            listOf(ReasoningLevel.AUTO, ReasoningLevel.LOW, ReasoningLevel.HIGH),
            model.composerReasoningLevels(),
        )
        assertFalse(ReasoningLevel.OFF in model.composerReasoningLevels())
        assertFalse(ReasoningLevel.MEDIUM in model.composerReasoningLevels())
        assertFalse(ReasoningLevel.XHIGH in model.composerReasoningLevels())
    }

    @Test fun noneEnablesOffAndMaxIsRepresentedByExtraHigh() {
        val model = reasoningModel(listOf("none", "low", "high", "max"), listOf("effort"))
        assertEquals(
            listOf(ReasoningLevel.OFF, ReasoningLevel.AUTO, ReasoningLevel.LOW,
                ReasoningLevel.HIGH, ReasoningLevel.XHIGH),
            model.composerReasoningLevels(ProviderSetting.OpenAI()),
        )
    }

    @Test fun toggleOnlyUsesOnOffWhenTheProviderSupportsTheDisableProtocol() {
        val model = reasoningModel(controls = listOf("toggle"))
        val provider = ProviderSetting.Claude()
        assertEquals(
            listOf(ReasoningLevel.OFF, ReasoningLevel.AUTO),
            model.composerReasoningLevels(provider),
        )
        assertEquals(ReasoningLevel.AUTO, model.effectiveComposerReasoningLevel(ReasoningLevel.XHIGH, provider))
    }

    @Test fun budgetOnlyWithoutVerifiedLevelBoundsDoesNotOfferFakeEfforts() {
        val model = reasoningModel(controls = listOf("budget_tokens"))
        assertEquals(listOf(ReasoningLevel.AUTO), model.composerReasoningLevels())
        assertEquals(ReasoningLevel.AUTO, model.effectiveComposerReasoningLevel(ReasoningLevel.HIGH))
    }

    @Test fun minimalAloneDoesNotMasqueradeAsLow() {
        val model = reasoningModel(listOf("minimal"), listOf("effort"))
        assertEquals(listOf(ReasoningLevel.AUTO), model.composerReasoningLevels())
    }

    @Test fun catalogModelWithNoAdvertisedReasoningControlsOnlyUsesAutomatic() {
        val catalogModel = reasoningModel().copy(metadataSource = "models.dev:sample/reasoning-model")
        assertEquals(listOf(ReasoningLevel.AUTO), catalogModel.composerReasoningLevels())
        assertEquals(ReasoningLevel.AUTO, catalogModel.effectiveComposerReasoningLevel(ReasoningLevel.XHIGH))
    }

    @Test fun oldModelWithoutCapabilityMetadataRetainsLegacyChoices() {
        val model = reasoningModel()
        assertEquals(ReasoningLevel.entries, model.composerReasoningLevels())
        assertEquals(ReasoningLevel.XHIGH, model.effectiveComposerReasoningLevel(ReasoningLevel.XHIGH))
    }

    @Test fun unsupportedSavedPreferenceIsResolvedPerModelWithoutMutatingIt() {
        val saved = ReasoningLevel.XHIGH
        val limited = reasoningModel(listOf("low", "medium", "high"), listOf("effort"))
        val unrestricted = reasoningModel(listOf("none", "low", "medium", "high", "xhigh"), listOf("effort"))
        assertEquals(ReasoningLevel.HIGH, limited.effectiveComposerReasoningLevel(saved))
        assertEquals(ReasoningLevel.XHIGH, unrestricted.effectiveComposerReasoningLevel(saved, ProviderSetting.OpenAI()))
        assertEquals(ReasoningLevel.XHIGH, saved)
    }

    @Test fun unsupportedMediumChoosesLowerAdvertisedLevelOrAuto() {
        val model = reasoningModel(listOf("low", "high"), listOf("effort"))
        assertEquals(ReasoningLevel.LOW, model.effectiveComposerReasoningLevel(ReasoningLevel.MEDIUM))
        val highOnly = reasoningModel(listOf("high"), listOf("effort"))
        assertEquals(ReasoningLevel.AUTO, highOnly.effectiveComposerReasoningLevel(ReasoningLevel.LOW))
    }

    @Test fun unavailableOffShowsAutomaticAndDoesNotCarryOffToNextModel() {
        val fixed = reasoningModel(listOf("low", "high"), listOf("effort"))
        val canDisable = reasoningModel(listOf("none", "low", "high"), listOf("effort"))
        assertEquals(ReasoningLevel.AUTO, fixed.effectiveComposerReasoningLevel(ReasoningLevel.OFF))
        assertEquals(ReasoningLevel.OFF, canDisable.effectiveComposerReasoningLevel(ReasoningLevel.OFF, ProviderSetting.OpenAI()))
    }

    @Test fun genericOpenAiCompatibleHostCannotClaimOffWithoutAWorkingDisableAdapter() {
        val model = reasoningModel(listOf("none", "low", "high"), listOf("effort"))
        val provider = ProviderSetting.OpenAI(baseUrl = "https://unverified.example/v1")
        assertEquals(
            listOf(ReasoningLevel.AUTO, ReasoningLevel.LOW, ReasoningLevel.HIGH),
            model.composerReasoningLevels(provider),
        )
        assertEquals(ReasoningLevel.AUTO, model.effectiveComposerReasoningLevel(ReasoningLevel.OFF, provider))
    }

    @Test fun togglesAreHiddenForUnknownOpenAiCompatibleHosts() {
        val model = reasoningModel(controls = listOf("toggle"))
        assertEquals(listOf(ReasoningLevel.AUTO), model.composerReasoningLevels(
            ProviderSetting.OpenAI(baseUrl = "https://unverified.example/v1")
        ))
    }

    @Test fun nativeGemini3DoesNotMislabelMinimalAsOff() {
        val model = reasoningModel(listOf("none", "low", "high"), listOf("effort"))
            .copy(modelId = "gemini-3-pro-preview")
        assertFalse(ReasoningLevel.OFF in model.composerReasoningLevels(ProviderSetting.Google()))
    }

    @Test fun unknownControlTypeNeverCreatesUnsupportedEffortOptions() {
        val model = reasoningModel(controls = listOf("provider_private"))
        assertEquals(listOf(ReasoningLevel.AUTO), model.composerReasoningLevels())
    }
}
