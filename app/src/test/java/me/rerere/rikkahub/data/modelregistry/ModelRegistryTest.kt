package me.rerere.rikkahub.data.modelregistry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Modality
import me.rerere.locallm.LocalRuntime

class ModelRegistryTest {
    @Test
    fun inferenceKeepsUncertainImageCapabilitiesUnverified() {
        val result = ModelCapabilityInference.infer(
            Model(type = ModelType.CHAT, inputModalities = listOf(Modality.IMAGE)),
        )
        assertTrue(ModelCapability.VISION in result.unverified)
        assertTrue(ModelCapability.OCR in result.unverified)
        assertTrue(ModelCapability.OCR !in result.verified)
    }

    @Test
    fun inferenceCombinesTypeAbilitiesAndBuiltInTools() {
        val result = ModelCapabilityInference.infer(
            Model(
                type = ModelType.CHAT,
                abilities = listOf(ModelAbility.REASONING, ModelAbility.TOOL),
                tools = setOf(BuiltInTools.ImageGeneration),
            ),
        )
        assertEquals(
            setOf(ModelCapability.CHAT, ModelCapability.REASONING, ModelCapability.TOOLS, ModelCapability.IMAGE_GENERATION),
            result.verified,
        )
    }

    @Test
    fun resolverUsesOverridesThenGlobalThenCompatibleModel() {
        val local = descriptor("local", ModelCapability.CHAT, local = true)
        val cloud = descriptor("cloud", ModelCapability.CHAT)
        assertEquals(
            ResolutionSource.ASSISTANT_OVERRIDE,
            (ModelResolver.resolve(ModelResolutionRequest(ModelCapability.CHAT, "local", "cloud", "cloud", listOf(local, cloud), ModelSourcePolicy.ANY)) as ModelResolution.Resolved).source,
        )
        assertEquals(
            ResolutionSource.GLOBAL_ASSIGNMENT,
            (ModelResolver.resolve(ModelResolutionRequest(ModelCapability.CHAT, globalAssignment = "cloud", models = listOf(local, cloud), sourcePolicy = ModelSourcePolicy.ANY)) as ModelResolution.Resolved).source,
        )
    }

    @Test
    fun resolverExcludesDisabledCapabilitiesAndProtectsCloudFallback() {
        val localDisabled = descriptor("local", ModelCapability.CHAT, local = true).copy(enabledCapabilities = emptySet())
        val cloud = descriptor("cloud", ModelCapability.CHAT)
        assertEquals(ModelResolution.NoCompatibleModel, ModelResolver.resolve(ModelResolutionRequest(ModelCapability.CHAT, models = listOf(localDisabled, cloud))))
        assertEquals("cloud", (ModelResolver.resolve(ModelResolutionRequest(ModelCapability.CHAT, models = listOf(localDisabled, cloud), sourcePolicy = ModelSourcePolicy.ANY)) as ModelResolution.Resolved).model.id)
    }

    @Test
    fun resolverAppliesLocalOnlyPolicyToExplicitCloudAssignments() {
        val cloud = descriptor("cloud", ModelCapability.VISION)

        assertEquals(
            ModelResolution.BlockedByPolicy("cloud", ModelSourcePolicy.LOCAL_ONLY),
            ModelResolver.resolve(
                ModelResolutionRequest(
                    capability = ModelCapability.VISION,
                    assistantOverride = "cloud",
                    models = listOf(cloud),
                    sourcePolicy = ModelSourcePolicy.LOCAL_ONLY,
                ),
            ),
        )
    }

    @Test
    fun resolverDoesNotFallThroughAnInvalidAssistantOverride() {
        val cloud = descriptor("cloud", ModelCapability.VISION)

        assertEquals(
            ModelResolution.InvalidOverride("missing", ModelFailureReason.NOT_FOUND),
            ModelResolver.resolve(
                ModelResolutionRequest(
                    capability = ModelCapability.VISION,
                    assistantOverride = "missing",
                    globalAssignment = "cloud",
                    models = listOf(cloud),
                    sourcePolicy = ModelSourcePolicy.ANY,
                ),
            ),
        )
    }

    @Test
    fun unverifiedCapabilitiesCanBeSelectedButNeverAutoResolved() {
        val model = descriptor("vision", ModelCapability.VISION)
            .copy(unverifiedCapabilities = setOf(ModelCapability.VISION))

        assertTrue(model.canExplicitlySelect(ModelCapability.VISION))
        assertTrue(!model.canAutoResolve(ModelCapability.VISION))
    }

    @Test
    fun rolesMatchCapabilitiesAcrossTabs() {
        val model = descriptor("multi", ModelCapability.CHAT, ModelCapability.VISION, ModelCapability.OCR)
        assertTrue(model.supports(ModelRole.CHAT.capability()))
        assertTrue(model.supports(ModelRole.VISION.capability()))
        assertTrue(model.supports(ModelRole.OCR.capability()))
    }

    private fun descriptor(id: String, vararg capabilities: ModelCapability, local: Boolean = false) = ModelDescriptor(
        id = id,
        displayName = id,
        source = if (local) ModelSource.Local(LocalRuntime.LiteRT, listOf(id)) else ModelSource.Cloud("provider", id),
        capabilities = capabilities.toSet(),
        lifecycle = if (local) ModelLifecycle.READY else ModelLifecycle.AVAILABLE,
        installed = local,
    )
}
