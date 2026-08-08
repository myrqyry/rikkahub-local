package me.rerere.locallm.task

import android.content.ContextWrapper
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class NpuTaskInferenceTest {

    @Test
    fun `create returns null when model file is missing`() {
        NpuTaskInference.resetProbe()
        val result = NpuTaskInference.create(
            ContextWrapper(null),
            "/no/such/model.tflite",
        )
        assertNull(result)
    }

    @Test
    fun `isNpuReady never throws on JVM where native libs are absent`() {
        NpuTaskInference.resetProbe()
        val ready = NpuTaskInference.isNpuReady(ContextWrapper(null))
        // On a JVM unit test there are no vendor NPU libs: the probe must come back
        // false instead of crashing on the missing native .so.
        assertFalse(ready)
    }
}
