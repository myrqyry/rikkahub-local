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

    private val requirements = ComputeRequirements(AcceleratorPreference.CPU, 1024L, 100L, 0L, 0L)

    @Test
    fun `commands round-trip with their discriminators`() {
        val commands: List<ComputeCommand> = listOf(
            ComputeCommand.Load(modelId = "model-a", requirements = requirements),
            ComputeCommand.Execute(
                modelId = "model-a",
                operation = "infer",
                input = mapOf("prompt" to "hello"),
                requirements = requirements,
            ),
            ComputeCommand.Release(modelId = "model-a"),
            ComputeCommand.Shutdown,
        )
        commands.forEach { command ->
            val encoded = json.encodeToString(ComputeCommand.serializer(), command)
            val decoded = json.decodeFromString(ComputeCommand.serializer(), encoded)
            assertEquals(command, decoded)
        }
        assert(json.encodeToString(ComputeCommand.serializer(), commands[0]).contains("compute_load"))
        assert(json.encodeToString(ComputeCommand.serializer(), commands[3]).contains("compute_shutdown"))
    }

    @Test
    fun `effects have stable names`() {
        assertEquals(4, ComputeEffect.entries.size)
        assertEquals(ComputeEffect.LOAD, ComputeEffect.valueOf("LOAD"))
        assertEquals(ComputeEffect.SHUTDOWN, ComputeEffect.valueOf("SHUTDOWN"))
    }
}
