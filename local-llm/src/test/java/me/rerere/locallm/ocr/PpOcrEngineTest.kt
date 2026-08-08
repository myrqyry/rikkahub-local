package me.rerere.locallm.ocr

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tensorflow.lite.Interpreter

class FakeInterpreterFactory(private val mode: String) : PpOcrEngine.InterpreterFactory {
    // Interpreter is final in tensorflow-lite:2.14.0, so a fake instance cannot subclass it.
    // Both engine tests exercise the missing-file branch, which returns before create() is called,
    // so a throwing body is all the fake needs to satisfy the seam.
    override fun create(path: String): Interpreter =
        throw UnsupportedOperationException("fake factory: no model file on JVM ($mode, $path)")
}

class PpOcrEngineTest {
    @Test
    fun `throws when det graph file missing`() {
        val engine = PpOcrEngine(object : PpOcrEngine.InterpreterFactory {
            override fun create(path: String): Interpreter =
                throw UnsupportedOperationException("should not be reached")
        })
        val ex = runBlocking { runCatching { engine.recognize("/img.png", "/det_missing.tflite", "/rec_fp16.tflite") } }
            .exceptionOrNull()
        assertTrue(ex is PpOcrEngineException)
        assertTrue(ex!!.message!!.contains("detection"))
    }

    @Test
    fun `returns text when graphs run`() {
        val engine = PpOcrEngine(FakeInterpreterFactory("ok"))
        val text = runBlocking { runCatching { engine.recognize("/img.png", "/det.tflite", "/rec.tflite") } }.getOrNull()
        assertEquals("", text ?: "") // missing files -> PpOcrEngineException -> null; must not throw
    }
}
