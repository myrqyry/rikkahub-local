package me.rerere.rikkahub.data.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.ModelType
import com.google.ai.edge.litertlm.Modality
import java.io.File
import me.rerere.rikkahub.data.rag.EmbeddingGemma2Model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * LiteRT-LM 0.18 ModelInfo smoke: no model initialization, no user preferences,
 * no downloads; reads models already installed on this Pixel.
 *
 * Manually invoke using adb shell am instrument, never connectedDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtModelInfoDeviceTest {
    @Test fun identifiesInstalledEmbeddingModelWithoutLoadingGraph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = EmbeddingGemma2Model.file(context)
        assertTrue("Model must already exist", model.isFile)
        ModelInfo.from(model.absolutePath).use { info ->
            assertEquals(ModelType.EMBEDDING, info.modelType)
            assertTrue(info.inputModalities().text)
            val capability = info as ModelInfo.Embedding
            assertEquals(768, capability.dimension())
            assertTrue(info.maxContextTokens() > 0)
            // Some CPU-capable model bundles omit optional backend metadata.
            // Log but do not invent accelerator support.
            android.util.Log.i(
                "LiteRtModelInfoTest",
                "Embedding modelType=${info.modelType} dimension=${capability.dimension()}" +
                    " context=${info.maxContextTokens()} backends=${info.supportedBackends(Modality.TEXT)}" +
                    " runtime=${info.minRuntimeVersion()}"
            )
        }
    }

    @Test fun identifiesInstalledChatModelWithoutLoadingGraph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = File(context.filesDir, "local-models/litert/LFM2.5-1.2B-Instruct_int4.litertlm")
        assertTrue("Chat model must already exist", model.isFile)
        ModelInfo.from(model.absolutePath).use { info ->
            assertEquals(ModelType.LLM, info.modelType)
            assertTrue(info.inputModalities().text)
            android.util.Log.i(
                "LiteRtModelInfoTest",
                "LLM modelType=${info.modelType} context=${info.maxContextTokens()}" +
                    " tools=${(info as ModelInfo.Llm).supportsFunctionCalling()}" +
                    " runtime=${info.minRuntimeVersion()}"
            )
        }
    }
}
