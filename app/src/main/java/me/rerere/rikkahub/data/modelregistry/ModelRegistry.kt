package me.rerere.rikkahub.data.modelregistry

import kotlinx.coroutines.flow.StateFlow

interface ModelRegistry {
    val models: StateFlow<List<ModelDescriptor>>
    val providers: StateFlow<List<ModelProviderDescriptor>>
    val assignments: StateFlow<ModelAssignments>

    suspend fun refreshProvider(providerId: String)
    suspend fun setCapabilityEnabled(modelId: String, capability: ModelCapability, enabled: Boolean)
    suspend fun assign(role: ModelRole, modelId: String?)
    suspend fun install(modelId: String)
    suspend fun rename(modelId: String, newDisplayName: String)
    suspend fun remove(modelId: String)
}
