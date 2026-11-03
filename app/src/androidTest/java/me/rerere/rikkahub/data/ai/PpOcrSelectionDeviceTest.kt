package me.rerere.rikkahub.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.pages.modelmanager.PpOcrVerifiedPackage
import me.rerere.rikkahub.ui.pages.modelmanager.withPpOcrSelected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

/** Pure constructed-settings tests. Never touch the live installed preferences. */
@RunWith(AndroidJUnit4::class)
class PpOcrSelectionDeviceTest {
    @Test fun selectingOcrPreservesOtherProvidersAndIsIdempotent() {
        val another = ProviderSetting.OpenAI(name = "Existing provider")
        val settings = Settings(providers = listOf(another))
        val selected = settings.withPpOcrSelected("/x/ppocr_det_fp16.tflite", "/x/ppocr_rec_fp16.tflite")
        assertEquals(2, selected.providers.size)
        assertEquals(another.id, selected.providers.first().id)
        val provider = selected.providers.filterIsInstance<ProviderSetting.TaskOcrLocal>().single()
        assertTrue(provider.enabled)
        assertEquals("/x/ppocr_det_fp16.tflite", provider.detModelPath)
        assertEquals("/x/ppocr_rec_fp16.tflite", provider.recModelPath)
        assertEquals(PpOcrVerifiedPackage.MODEL_ID, provider.models.single().modelId)
        assertEquals(ModelType.IMAGE, provider.models.single().type)
        assertEquals(provider.models.single().id, selected.ocrModelId)
        val repeated = selected.withPpOcrSelected("/x/ppocr_det_fp16.tflite", "/x/ppocr_rec_fp16.tflite")
        assertEquals(selected.ocrModelId, repeated.ocrModelId)
        assertEquals(provider.id, repeated.providers.filterIsInstance<ProviderSetting.TaskOcrLocal>().single().id)
    }

    @Test fun selectingOcrPreservesExistingOcrProviderAndOtherModels() {
        val original = ProviderSetting.TaskOcrLocal(
            id = Uuid.random(),
            enabled = false,
            models = listOf(Model(modelId = "other-ocr")),
        )
        val selected = Settings(providers = listOf(original))
            .withPpOcrSelected("/x/ppocr_det_fp16.tflite", "/x/ppocr_rec_fp16.tflite")
        val provider = selected.providers.single() as ProviderSetting.TaskOcrLocal
        assertEquals(original.id, provider.id)
        assertTrue(provider.enabled)
        assertEquals(setOf("other-ocr", PpOcrVerifiedPackage.MODEL_ID),
            provider.models.map { it.modelId }.toSet())
        assertEquals(PpOcrVerifiedPackage.MODEL_ID,
            provider.models.single { it.id == selected.ocrModelId }.modelId)
    }
}
