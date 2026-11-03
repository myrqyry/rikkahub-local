package me.rerere.locallm.litert

import org.junit.Assert.*
import org.junit.Test

class SparkCatalogTest {
    @Test fun `Spark experimental catalog entry pins its bytes and stays out of agent tools`() {
        val entry = requireNotNull(LiteRtCatalog.ENTRIES.find { it.modelId == "litert-community/Spark-X2.6-1.7B" })
        assertFalse(entry.recommended)
        assertEquals("https://huggingface.co/litert-community/Spark-X2.6-1.7B/resolve/bc1ef142dcbeacddd597ebc738245207d8793755/Spark-X2.6-1.7B_int4-mlp.litertlm", entry.resolveUrl())
        assertEquals("0911f7ebc4ef98ae5dadcbd5a35214ca1aaf63dffd58bcf5128032c3ab859cd5", entry.sha256)
        assertEquals(1705807792L, entry.sizeBytes)
        val config = entry.config()
        assertFalse(config.supportsToolCalling)
        assertTrue(config.supportsThinking)
        assertFalse(config.supportsImage)
        assertFalse(config.supportsAudio)
        assertEquals(4096, config.maxContextLength)
        assertEquals(listOf("gpu", "cpu"), config.preferredAccelerators)
    }
}
