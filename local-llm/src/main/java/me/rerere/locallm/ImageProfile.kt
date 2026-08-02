package me.rerere.locallm

data class ImageProfile(
    val id: String,
    val name: String,
    val modelId: String,
    val width: Int = 512,
    val height: Int = 512,
    val steps: Int = 20,
    val cfgScale: Float = 7.0f,
    val seed: Int = -1,
    val negativePrompt: String = "",
)

class ImageProfileStore {
    private val profiles = mutableMapOf<String, ImageProfile>()
    fun save(profile: ImageProfile) { profiles[profile.id] = profile }
    fun delete(id: String) { profiles.remove(id) }
    fun getById(id: String): ImageProfile? = profiles[id]
    fun list(): List<ImageProfile> = profiles.values.toList()
    fun listByModelId(modelId: String): List<ImageProfile> =
        profiles.values.filter { it.modelId == modelId }
}
