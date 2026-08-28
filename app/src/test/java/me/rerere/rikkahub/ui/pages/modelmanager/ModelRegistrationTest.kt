package me.rerere.rikkahub.ui.pages.modelmanager

import kotlin.uuid.Uuid
import me.rerere.ai.provider.LITERT_PROVIDER_ID
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRegistrationTest {
    @Test
    fun `registration enables litert and adds model once`() {
        val model = Model(
            modelId = "flux2-klein",
            displayName = "FLUX.2-klein",
            type = ModelType.IMAGE,
            inputModalities = listOf(Modality.TEXT),
            outputModalities = listOf(Modality.IMAGE),
        )
        val initial = Settings(providers = listOf(ProviderSetting.LiteRtLocal()))

        val once = ModelRegistration.register(initial, LITERT_PROVIDER_ID, model)
        val twice = ModelRegistration.register(once, LITERT_PROVIDER_ID, model)
        val provider = twice.providers.single() as ProviderSetting.LiteRtLocal

        assertTrue(provider.enabled)
        assertEquals(1, provider.models.size)
        assertEquals(model.modelId, provider.models.single().modelId)
    }

    @Test
    fun `unsupported provider is rejected without changing settings`() {
        val initial = Settings(providers = emptyList())

        try {
            ModelRegistration.register(initial, Uuid.random(), sampleModel())
            throw AssertionError("registration should reject unsupported providers")
        } catch (_: IllegalStateException) {
            assertTrue(initial.providers.isEmpty())
        }
    }

    private fun sampleModel() = Model(
        modelId = "model",
        displayName = "Model",
        type = ModelType.CHAT,
        inputModalities = listOf(Modality.TEXT),
        outputModalities = listOf(Modality.TEXT),
    )
}
