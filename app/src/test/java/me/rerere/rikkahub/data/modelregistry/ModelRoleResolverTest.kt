package me.rerere.rikkahub.data.modelregistry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import me.rerere.locallm.LocalRuntime
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRoleResolverTest {
    @Test
    fun assistantOverrideUsesSyntheticRegistryId() {
        val local = descriptor("local:litert:vision.litertlm", local = true)
        val registry = TestRegistry(listOf(local), ModelAssignments(
            defaults = mapOf(ModelRole.VISION to "missing"),
        ))
        val assistant = Assistant(modelOverrides = mapOf(
            ModelRole.VISION to RegistryModelId(local.id),
        ))

        val result = ModelRoleResolver(registry).resolve(
            ModelRole.VISION,
            assistant,
            Settings(),
            ModelSourcePolicy.LOCAL_ONLY,
        )

        assertEquals(local.id, (result as ModelResolution.Resolved).model.id)
    }

    @Test
    fun invalidAssistantOverrideDoesNotFallThroughToGlobalAssignment() {
        val cloud = descriptor("cloud", local = false)
        val registry = TestRegistry(listOf(cloud), ModelAssignments(
            defaults = mapOf(ModelRole.VISION to cloud.id),
        ))
        val assistant = Assistant(modelOverrides = mapOf(
            ModelRole.VISION to RegistryModelId("deleted"),
        ))

        val result = ModelRoleResolver(registry).resolve(
            ModelRole.VISION,
            assistant,
            Settings(),
            ModelSourcePolicy.ANY,
        )

        assertTrue(result is ModelResolution.InvalidOverride)
    }

    @Test
    fun localOnlyPolicyBlocksCloudGlobalAssignment() {
        val cloud = descriptor("cloud", local = false)
        val registry = TestRegistry(listOf(cloud), ModelAssignments(
            defaults = mapOf(ModelRole.VISION to cloud.id),
        ))

        val result = ModelRoleResolver(registry).resolve(
            ModelRole.VISION,
            Assistant(),
            Settings(),
            ModelSourcePolicy.LOCAL_ONLY,
        )

        assertTrue(result is ModelResolution.BlockedByPolicy)
    }

    @Test
    fun assistantDefaultsDecodeWithoutNewFields() {
        val assistant = Json.decodeFromString<Assistant>("{}")

        assertTrue(assistant.modelOverrides.isEmpty())
        assertTrue(assistant.allowCloudAttachmentProcessing)
        assertTrue(assistant.allowCloudImageProcessing)
    }

    private fun descriptor(id: String, local: Boolean) = ModelDescriptor(
        id = id,
        displayName = id,
        source = if (local) ModelSource.Local(LocalRuntime.LiteRT, listOf(id)) else ModelSource.Cloud("provider", id),
        capabilities = setOf(ModelCapability.VISION),
        lifecycle = if (local) ModelLifecycle.READY else ModelLifecycle.AVAILABLE,
        installed = local,
    )

    private class TestRegistry(
        models: List<ModelDescriptor>,
        assignments: ModelAssignments,
    ) : ModelRegistry {
        override val models = MutableStateFlow(models)
        override val providers = MutableStateFlow(emptyList<ModelProviderDescriptor>())
        override val assignments = MutableStateFlow(assignments)

        override suspend fun refreshProvider(providerId: String) = error("not used")
        override suspend fun setCapabilityEnabled(modelId: String, capability: ModelCapability, enabled: Boolean) = error("not used")
        override suspend fun assign(role: ModelRole, modelId: String?) = error("not used")
        override suspend fun install(modelId: String) = error("not used")
        override suspend fun remove(modelId: String) = error("not used")
    }
}
