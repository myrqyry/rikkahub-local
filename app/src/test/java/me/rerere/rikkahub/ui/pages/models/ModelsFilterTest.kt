package me.rerere.rikkahub.ui.pages.models

import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun descriptor(
    id: String,
    displayName: String,
    capabilities: Set<ModelCapability>,
    source: ModelSource = ModelSource.Local(me.rerere.locallm.LocalRuntime.LiteRT),
) = ModelDescriptor(
    id = id,
    displayName = displayName,
    source = source,
    capabilities = capabilities,
    enabledCapabilities = capabilities,
    lifecycle = ModelLifecycle.READY,
)

class ModelsFilterTest {
    @Test
    fun `ALL matches everything`() {
        assertTrue(ModelsFilter.ALL.matches(descriptor("a", "x", setOf(ModelCapability.CHAT))))
    }

    @Test
    fun `CHAT matches only chat`() {
        assertTrue(ModelsFilter.CHAT.matches(descriptor("a", "x", setOf(ModelCapability.CHAT))))
        assertFalse(ModelsFilter.CHAT.matches(descriptor("b", "y", setOf(ModelCapability.VISION))))
    }

    @Test
    fun `VISION covers OCR and document analysis`() {
        setOf(
            ModelCapability.VISION,
            ModelCapability.OCR,
            ModelCapability.DOCUMENT_ANALYSIS,
        ).forEach { cap ->
            assertTrue(ModelsFilter.VISION.matches(descriptor("a", "x", setOf(cap))))
        }
        assertFalse(ModelsFilter.VISION.matches(descriptor("b", "y", setOf(ModelCapability.CHAT))))
    }

    @Test
    fun `IMAGE covers generation and editing`() {
        assertTrue(ModelsFilter.IMAGE.matches(descriptor("a", "x", setOf(ModelCapability.IMAGE_GENERATION))))
        assertTrue(ModelsFilter.IMAGE.matches(descriptor("b", "y", setOf(ModelCapability.IMAGE_EDITING))))
        assertFalse(ModelsFilter.IMAGE.matches(descriptor("c", "z", setOf(ModelCapability.CHAT))))
    }

    @Test
    fun `AUDIO covers tts stt and understanding`() {
        setOf(
            ModelCapability.TEXT_TO_SPEECH,
            ModelCapability.SPEECH_TO_TEXT,
            ModelCapability.AUDIO_UNDERSTANDING,
        ).forEach { cap ->
            assertTrue(ModelsFilter.AUDIO.matches(descriptor("a", "x", setOf(cap))))
        }
    }

    @Test
    fun `RETRIEVAL covers embeddings and reranking`() {
        assertTrue(ModelsFilter.RETRIEVAL.matches(descriptor("a", "x", setOf(ModelCapability.EMBEDDINGS))))
        assertTrue(ModelsFilter.RETRIEVAL.matches(descriptor("b", "y", setOf(ModelCapability.RERANKING))))
    }

    @Test
    fun `tab maps to nearest filter`() {
        assertTrue(ModelTab.ALL.toModelsFilter() == ModelsFilter.ALL)
        assertTrue(ModelTab.CHAT.toModelsFilter() == ModelsFilter.CHAT)
        assertTrue(ModelTab.VISION.toModelsFilter() == ModelsFilter.VISION)
        assertTrue(ModelTab.IMAGE.toModelsFilter() == ModelsFilter.IMAGE)
        assertTrue(ModelTab.SPEECH.toModelsFilter() == ModelsFilter.AUDIO)
        assertTrue(ModelTab.EMBEDDINGS.toModelsFilter() == ModelsFilter.RETRIEVAL)
        assertTrue(ModelTab.TASK.toModelsFilter() == ModelsFilter.VISION)
        assertTrue(ModelTab.OTHER.toModelsFilter() == ModelsFilter.ALL)
    }

    @Test
    fun `search matches display name and id case-insensitively`() {
        val model = descriptor("local:litert:gemma", "Gemma 4 E2B", setOf(ModelCapability.CHAT))
        assertTrue(searchMatches(model, "gemma"))
        assertTrue(searchMatches(model, "GEMMA"))
        assertTrue(searchMatches(model, "litert"))
        assertFalse(searchMatches(model, "mistral"))
    }
}
