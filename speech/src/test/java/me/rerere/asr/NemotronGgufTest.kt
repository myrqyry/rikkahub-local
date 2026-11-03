package me.rerere.asr

import org.junit.Assert.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Test

class NemotronGgufTest {
    @Test fun `pinned artifact has the verified Android test identity`() {
        assertEquals(559647200L, NemotronGgufModel.sizeBytes)
        assertEquals("86429e8c4f7fdcf9b3312269ad1ca6669478ba7805331c4aea7a2e33e9910d65", NemotronGgufModel.sha256)
        assertEquals("https://huggingface.co/handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf/resolve/53ce288f69326b415aeef030cfcb02a08a8096dd/nemotron-3.5-asr-streaming-0.6b-Q5_K_M.gguf", NemotronGgufModel.downloadUrl)
    }

    @Test fun `Nemotron provider serializes as a distinct provider and preserves settings`() {
        val original: ASRProviderSetting = ASRProviderSetting.NemotronGguf(
            modelPath = "/models/verified.gguf", language = "es-ES", rightContext = 0,
        )
        val json = Json { ignoreUnknownKeys = true }
        val saved = json.encodeToString(ASRProviderSetting.serializer(), original)
        assertEquals(original, json.decodeFromString<ASRProviderSetting>(saved))
        assertTrue(ASRProviderSetting.Types.contains(ASRProviderSetting.NemotronGguf::class))
    }

    @Test fun `Nemotron provider has explicit language and streaming options`() {
        val setting = ASRProviderSetting.NemotronGguf()
        assertEquals("en-US", setting.language)
        assertEquals(1120, setting.chunkMs)
        assertEquals(3, setting.rightContext)
        assertEquals(4, setting.threads)
        assertEquals(setting.id, setting.copyProvider(name = "Local Nemotron").id)
    }
}
