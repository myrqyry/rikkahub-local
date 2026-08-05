package me.rerere.rikkahub.ui.pages.models

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.modelregistry.LegacyModelAssignmentAdapter
import me.rerere.rikkahub.data.modelregistry.ModelAssignments
import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelProviderDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelRegistry
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnifiedModelsViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test
    fun filtersByCapabilityAndSearchWithoutChangingRegistryInventory() = runBlocking {
        val chat = model("chat", "Chat", ModelCapability.CHAT)
        val vision = model("vision", "Camera Vision", ModelCapability.VISION)
        val image = model("image", "Image", ModelCapability.IMAGE_GENERATION)
        val vm = viewModelWith(listOf(chat, vision, image))

        vm.setTab(ModelTab.VISION)
        vm.setSearch("camera")

        assertEquals(listOf(vision), vm.visibleModels.first())
        assertEquals(listOf(chat, vision, image), vm.allModels.first())
    }

    @Test
    fun assignmentsPreserveTitleTranslationAndDistinctVisionOcr() = runBlocking {
        val chat = model("chat", "Chat", ModelCapability.CHAT)
        val vision = model("vision", "Vision", ModelCapability.VISION)
        val ocr = model("ocr", "OCR", ModelCapability.OCR)
        val legacy = FakeLegacy("title", "translation")
        val vm = viewModelWith(
            models = listOf(chat, vision, ocr),
            assignments = ModelAssignments(
                defaults = mapOf(
                    ModelRole.CHAT to chat.id,
                    ModelRole.VISION to vision.id,
                    ModelRole.OCR to ocr.id,
                ),
            ),
            legacy = legacy,
        )

        assertEquals(chat.id, vm.assignments.first().defaults[ModelRole.CHAT])
        assertEquals(vision.id, vm.assignments.first().defaults[ModelRole.VISION])
        assertEquals(ocr.id, vm.assignments.first().defaults[ModelRole.OCR])
        assertEquals("title", vm.legacyAssignments.first().titleModelId)
        assertEquals("translation", vm.legacyAssignments.first().translationModelId)
    }

    @Test
    fun selectedUnavailableModelProducesRepairStateInsteadOfReplacement() = runBlocking {
        val missingId = "missing"
        val vm = viewModelWith(
            models = listOf(model("chat", "Chat", ModelCapability.CHAT)),
            assignments = ModelAssignments(defaults = mapOf(ModelRole.CHAT to missingId)),
        )

        assertEquals(RepairState.ModelUnavailable(ModelRole.CHAT, missingId), vm.repairState.first())
    }

    @Test
    fun filtersDisabledProvidersAndDisabledCapabilities() = runBlocking {
        val disabledProvider = model("disabled", "Disabled", ModelCapability.CHAT)
            .copy(providerEnabled = false)
        val disabledCapability = model("off", "Capability Off", ModelCapability.CHAT)
            .copy(enabledCapabilities = emptySet())
        val vm = viewModelWith(
            models = listOf(disabledProvider, disabledCapability),
            providers = listOf(ModelProviderDescriptor("provider", "Provider", true, listOf("off"))),
        )

        vm.setTab(ModelTab.CHAT)
        assertEquals(emptyList<ModelDescriptor>(), vm.visibleModels.value)
    }

    @Test
    fun validatesLocalReadinessBeforePersistence() = runBlocking {
        val local = model("local", "Local", ModelCapability.CHAT).copy(
            source = me.rerere.rikkahub.data.modelregistry.ModelSource.Local(
                me.rerere.locallm.LocalRuntime.LiteRT,
            ),
        )
        val registry = FakeRegistry(listOf(local), ModelAssignments())
        val vm = UnifiedModelsViewModel(registry, FakeLegacy(), scope = scope)

        vm.assign(ModelRole.CHAT, local.id).join()

        assertEquals(0, registry.assignCalls)
        assertEquals("Model local is not ready", vm.operationError.first())
    }

    @Test
    fun validatesDisabledProviderAndCapabilityBeforePersistence() = runBlocking {
        val disabledProvider = model("provider-off", "Provider Off", ModelCapability.CHAT)
            .copy(providerEnabled = false)
        val disabledCapability = model("capability-off", "Capability Off", ModelCapability.CHAT)
            .copy(enabledCapabilities = emptySet())
        val registry = FakeRegistry(listOf(disabledProvider, disabledCapability), ModelAssignments())
        val vm = UnifiedModelsViewModel(registry, FakeLegacy(), scope = scope)

        vm.assign(ModelRole.CHAT, disabledProvider.id).join()
        assertEquals(0, registry.assignCalls)
        assertEquals("Model provider-off provider is disabled", vm.operationError.value)

        vm.assign(ModelRole.CHAT, disabledCapability.id).join()
        assertEquals(0, registry.assignCalls)
        assertEquals("Model capability-off does not support CHAT", vm.operationError.value)
    }

    @Test
    fun sourceProviderFiltersAndClearOperationsRestoreInventory() = runBlocking {
        val local = model("local", "Local", ModelCapability.CHAT).copy(
            source = me.rerere.rikkahub.data.modelregistry.ModelSource.Local(
                me.rerere.locallm.LocalRuntime.LiteRT,
            ),
            lifecycle = ModelLifecycle.READY,
        )
        val cloud = model("cloud", "Cloud", ModelCapability.CHAT)
        val vm = viewModelWith(
            models = listOf(local, cloud),
            providers = listOf(ModelProviderDescriptor("provider", "Provider", true, listOf(cloud.id))),
        )

        vm.setSourceFilter(ModelSourceFilter.LOCAL)
        assertEquals(listOf(local), vm.visibleModels.first())

        vm.setSourceFilter(ModelSourceFilter.ALL)
        vm.setProviderFilter("provider")
        assertEquals(listOf(cloud), vm.visibleModels.first())

        vm.clearProviderFilter()
        assertEquals(listOf(local, cloud), vm.visibleModels.first())
        vm.clearSourceFilter()
        assertEquals(listOf(local, cloud), vm.visibleModels.first())
    }

    @Test
    fun legacyRepairStateIncludesTitleAndTranslationAndClearKeepsTranslationNonNull() = runBlocking {
        val legacy = FakeLegacy(title = "missing-title", translation = "missing-translation")
        val vm = viewModelWith(models = emptyList(), legacy = legacy)

        assertEquals(
            RepairState.LegacyModelUnavailable(LegacyAssignmentKey.TITLE, "missing-title"),
            vm.repairState.first(),
        )
        vm.assignTranslation(null).join()
        assertEquals("Translation model assignment cannot be cleared", vm.operationError.value)
        assertEquals("missing-translation", legacy.translationModelId.value)
        vm.assignTitle(null).join()
        assertNull(legacy.titleModelId.value)
    }

    private fun viewModelWith(
        models: List<ModelDescriptor>,
        assignments: ModelAssignments = ModelAssignments(),
        legacy: FakeLegacy = FakeLegacy(),
        providers: List<ModelProviderDescriptor> = listOf(
            ModelProviderDescriptor("provider", "Provider", enabled = true, modelIds = models.map { it.id }),
        ),
    ) = UnifiedModelsViewModel(
        registry = FakeRegistry(models, assignments, providers),
        legacyAdapter = legacy,
        scope = scope,
    )

    private fun model(id: String, name: String, capability: ModelCapability) = ModelDescriptor(
        id = id,
        displayName = name,
        source = ModelSource.Cloud("provider", id),
        capabilities = setOf(capability),
        lifecycle = ModelLifecycle.AVAILABLE,
    )

    private class FakeRegistry(
        models: List<ModelDescriptor>,
        assignment: ModelAssignments,
        providers: List<ModelProviderDescriptor> = emptyList(),
    ) : ModelRegistry {
        var assignCalls = 0
        override val models = MutableStateFlow(models)
        override val providers = MutableStateFlow(providers)
        override val assignments = MutableStateFlow(assignment)
        override suspend fun refreshProvider(providerId: String) = Unit
        override suspend fun setCapabilityEnabled(modelId: String, capability: ModelCapability, enabled: Boolean) = Unit
        override suspend fun assign(role: ModelRole, modelId: String?) { assignCalls++ }
        override suspend fun install(modelId: String) = Unit
        override suspend fun remove(modelId: String) = Unit
    }

    private class FakeLegacy(title: String? = null, translation: String? = null) : LegacyModelAssignmentAdapter {
        override val titleModelId = MutableStateFlow(title)
        override val translationModelId = MutableStateFlow(translation)
        override suspend fun setTitleModel(modelId: String?) { titleModelId.value = modelId }
        override suspend fun setTranslationModel(modelId: String?) {
            if (modelId == null) throw UnsupportedOperationException("Translation model assignment cannot be cleared")
            translationModelId.value = modelId
        }
    }
}
