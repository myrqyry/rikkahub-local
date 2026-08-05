package me.rerere.rikkahub.ui.pages.models.components

import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelSource
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelAssignmentsSectionTest {
    private val models = listOf(
        model("chat", ModelCapability.CHAT),
        model("vision", ModelCapability.VISION),
        model("ocr", ModelCapability.OCR),
        model("vision-ocr", ModelCapability.VISION, ModelCapability.OCR),
        model("image", ModelCapability.IMAGE_GENERATION),
        model("embedding", ModelCapability.EMBEDDINGS),
    )

    @Test
    fun filtersEachAssignmentRoleByItsCapability() {
        assertEquals(listOf("chat"), compatibleAssignments(ModelRole.CHAT, models).map { it.id })
        assertEquals(listOf("vision", "vision-ocr"), compatibleAssignments(ModelRole.VISION, models).map { it.id })
        assertEquals(listOf("ocr", "vision-ocr"), compatibleAssignments(ModelRole.OCR, models).map { it.id })
        assertEquals(listOf("image"), compatibleAssignments(ModelRole.IMAGE_GENERATION, models).map { it.id })
        assertEquals(listOf("embedding"), compatibleAssignments(ModelRole.EMBEDDINGS, models).map { it.id })
    }

    @Test
    fun visionOnlyModelDoesNotBecomeAnOcrFallback() {
        val visionOnly = model("vision-only", ModelCapability.VISION)

        assertEquals(emptyList<ModelDescriptor>(), compatibleAssignments(ModelRole.OCR, listOf(visionOnly)))
    }

    @Test
    fun excludesDisabledProvidersAndUnreadyLocalModels() {
        val disabled = model("disabled", ModelCapability.CHAT).copy(providerEnabled = false)
        val local = model("local", ModelCapability.CHAT).copy(
            source = ModelSource.Local(me.rerere.locallm.LocalRuntime.LiteRT),
            lifecycle = ModelLifecycle.DOWNLOADING,
        )
        val installed = local.copy(id = "installed", installed = true)

        assertEquals(listOf("installed"), compatibleAssignments(ModelRole.CHAT, listOf(disabled, local, installed)).map { it.id })
    }

    private fun model(id: String, vararg capabilities: ModelCapability) = ModelDescriptor(
        id = id,
        displayName = id,
        source = ModelSource.Cloud("provider", id),
        capabilities = capabilities.toSet(),
    )
}
