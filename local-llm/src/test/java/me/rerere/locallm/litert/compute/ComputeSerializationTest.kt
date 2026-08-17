package me.rerere.locallm.litert.compute

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `compute ref stringifies with prefix`() {
        assertEquals("compute:main", ComputeRef("main").toString())
    }

    @Test
    fun `requirements round-trip`() {
        val requirements = ComputeRequirements(
            accelerator = AcceleratorPreference.GPU,
            estimatedModelBytes = 2L * 1024 * 1024 * 1024,
            maxCpuMillis = 1000L,
            maxGpuMillis = 500L,
            maxAcceleratorMemoryBytes = 1024L * 1024 * 1024,
        )
        val encoded = json.encodeToString(ComputeRequirements.serializer(), requirements)
        val decoded = json.decodeFromString(ComputeRequirements.serializer(), encoded)
        assertEquals(requirements, decoded)
        assert(encoded.contains("GPU"))
    }

    @Test
    fun `every accelerator preference round-trips`() {
        AcceleratorPreference.entries.forEach { preference ->
            val requirements = ComputeRequirements(preference, 1L, 1L, 1L, 1L)
            val decoded = json.decodeFromString(
                ComputeRequirements.serializer(),
                json.encodeToString(ComputeRequirements.serializer(), requirements),
            )
            assertEquals(requirements, decoded)
        }
    }
}
