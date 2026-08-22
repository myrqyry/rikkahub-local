package me.rerere.locallm

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GgufClassifierTest {

    private fun putU32(out: ByteArrayOutputStream, v: Int) {
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
    }

    private fun putU64(out: ByteArrayOutputStream, v: Long) {
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array())
    }

    private fun putString(out: ByteArrayOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        putU64(out, b.size.toLong())
        out.write(b)
    }

    // Builds a minimal GGUF v3: header + optional metadata KV (general.architecture)
    // + a single tensor-info entry with the given name. No data payload.
    private fun buildGguf(architecture: String?, tensorName: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x47, 0x47, 0x55, 0x46.toByte())) // "GGUF"
        putU32(out, 3) // version
        putU64(out, 1) // tensor_count
        putU64(out, if (architecture != null) 1L else 0L) // metadata_kv_count

        if (architecture != null) {
            putString(out, "general.architecture")
            putU32(out, 8) // GGUFValueType.STRING
            putString(out, architecture)
        }

        // tensor-info entry
        putString(out, tensorName)
        putU32(out, 2) // n_dims
        putU32(out, 8)
        putU32(out, 8)
        putU32(out, 32) // ggml_type Q4_K... (irrelevant for classification)
        putU64(out, 0) // offset
        return out.toByteArray()
    }

    @Test
    fun llmArchitectureRoutesToLlamaCpp() {
        val bytes = buildGguf("llama", "token_embd.weight")
        assertEquals(LocalRuntime.LlamaCpp, GgufClassifier.classify(bytes))
    }

    @Test
    fun qwenArchitectureRoutesToLlamaCpp() {
        val bytes = buildGguf("qwen2", "model.embed_tokens.weight")
        assertEquals(LocalRuntime.LlamaCpp, GgufClassifier.classify(bytes))
    }

    @Test
    fun imageArchitectureRoutesToStableDiffusion() {
        val bytes = buildGguf("sd", "cond_stage_model.transformer.text_model.embeddings.position_embedding.weight")
        assertEquals(LocalRuntime.StableDiffusion, GgufClassifier.classify(bytes))
    }

    @Test
    fun noArchitectureCondStageTensorRoutesToStableDiffusion() {
        // The vendored sd.cpp writer emits no metadata; tensors follow the header.
        val bytes = buildGguf(null, "cond_stage_model.transformer.text_model.embeddings.position_embedding.weight")
        assertEquals(LocalRuntime.StableDiffusion, GgufClassifier.classify(bytes))
    }

    @Test
    fun noArchitectureDiffusionModelTensorRoutesToStableDiffusion() {
        val bytes = buildGguf(null, "model.diffusion_model.blocks.0.resblocks.0.weight")
        assertEquals(LocalRuntime.StableDiffusion, GgufClassifier.classify(bytes))
    }

    @Test
    fun noArchitectureLlmTensorRoutesToLlamaCpp() {
        val bytes = buildGguf(null, "token_embd.weight")
        assertEquals(LocalRuntime.LlamaCpp, GgufClassifier.classify(bytes))
    }

    @Test
    fun nonGgufReturnsNull() {
        assertNull(GgufClassifier.classify("not a gguf file at all".toByteArray()))
    }

    @Test
    fun truncatedHeaderReturnsNull() {
        val bytes = buildGguf("llama", "token_embd.weight")
        assertNull(GgufClassifier.classify(bytes.copyOf(10)))
    }

    @Test
    fun classifyFileDelegatesToByteArrayClassifier() {
        val tmp = File.createTempFile("gguf-test", ".gguf")
        try {
            tmp.writeBytes(buildGguf("qwen2", "model.embed_tokens.weight"))
            assertEquals(LocalRuntime.LlamaCpp, GgufClassifier.classifyFile(tmp))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun classifyFileMissingFileReturnsNull() {
        assertNull(GgufClassifier.classifyFile(File("/nonexistent/missing.gguf")))
    }
}
