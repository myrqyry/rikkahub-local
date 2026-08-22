package me.rerere.locallm.llamacpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class MmprojLocatorTest {
    @Test
    fun `finds mmproj named after the model`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "mmproj-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            File(dir, "qwen2-vl-2b-instruct-q4_k_m.gguf").writeBytes(ByteArray(16))
            val mmproj = File(dir, "qwen2-vl-2b-instruct-q4_k_m.mmproj.gguf").apply { writeBytes(ByteArray(16)) }
            assertEquals(
                mmproj,
                MmprojLocator.findMmproj(File(dir, "qwen2-vl-2b-instruct-q4_k_m.gguf")),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `falls back to plain mmproj gguf sibling`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "mmproj-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            File(dir, "qwen2-vl-2b-instruct-q4_k_m.gguf").writeBytes(ByteArray(16))
            val mmproj = File(dir, "mmproj.gguf").apply { writeBytes(ByteArray(16)) }
            assertEquals(
                mmproj,
                MmprojLocator.findMmproj(File(dir, "qwen2-vl-2b-instruct-q4_k_m.gguf")),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `prefers model-named mmproj over plain sibling`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "mmproj-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            File(dir, "qwen2-vl-2b-instruct-q4_k_m.gguf").writeBytes(ByteArray(16))
            val named = File(dir, "qwen2-vl-2b-instruct-q4_k_m.mmproj.gguf").apply { writeBytes(ByteArray(16)) }
            File(dir, "mmproj.gguf").writeBytes(ByteArray(16))
            assertEquals(
                named,
                MmprojLocator.findMmproj(File(dir, "qwen2-vl-2b-instruct-q4_k_m.gguf")),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `returns null when no mmproj sibling exists`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "mmproj-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            File(dir, "qwen2-vl-2b-instruct-q4_k_m.gguf").writeBytes(ByteArray(16))
            assertNull(MmprojLocator.findMmproj(File(dir, "qwen2-vl-2b-instruct-q4_k_m.gguf")))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `returns null for missing model file`() {
        assertNull(MmprojLocator.findMmproj(File("/nonexistent/qwen2-vl-2b-instruct-q4_k_m.gguf")))
    }
}
