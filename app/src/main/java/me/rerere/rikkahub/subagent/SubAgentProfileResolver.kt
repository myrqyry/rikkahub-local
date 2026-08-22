package me.rerere.rikkahub.subagent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** A named, reusable sub-agent definition — prompt and optional model, dispatchable by name. */
@Serializable
data class SubAgentProfile(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String = "",
    @SerialName("system_prompt") val systemPrompt: String = "",
    @SerialName("model_id") val modelId: Uuid? = null,
    @SerialName("enabled") val enabled: Boolean = true,
)

/**
 * Resolves an `agent` name passed to sub-agent dispatch against the ENABLED profiles.
 * Disabled profiles are neither resolvable nor discoverable. `null`/blank → [NotRequested]
 * (no profile — plain dispatch). A non-existent name fails with the list of valid names so the
 * model can correct itself instead of silently running without the requested profile.
 */
internal object SubAgentProfileResolver {

    sealed class Result {
        data object NotRequested : Result()
        data class Resolved(val profile: SubAgentProfile) : Result()
        data class Failed(val message: String) : Result()
    }

    fun enabledProfiles(profiles: List<SubAgentProfile>): List<SubAgentProfile> =
        profiles.filter { it.enabled }

    fun resolve(agentName: String?, profiles: List<SubAgentProfile>): Result {
        val name = agentName?.trim().orEmpty()
        if (name.isEmpty()) return Result.NotRequested

        val enabled = enabledProfiles(profiles)
        val matches = enabled.filter { it.name.equals(name, ignoreCase = true) }
        return when {
            matches.size > 1 -> Result.Failed(
                "agent \"$name\" matches multiple sub-agent profiles — rename one of these duplicates: " +
                    matches.joinToString(", ") { it.name },
            )

            matches.size == 1 -> Result.Resolved(matches[0])
            enabled.isEmpty() -> Result.Failed(
                "agent \"$name\" did not match any sub-agent profile, and no profiles are configured.",
            )

            else -> Result.Failed(
                "agent \"$name\" did not match any enabled sub-agent profile. Available: " +
                    enabled.joinToString { it.name },
            )
        }
    }

    /**
     * Combined model resolution: an explicit `model_id` always wins — when it resolves, and when
     * it fails (a bad explicit model_id must surface, not be papered over). Only when model_id was
     * [SubAgentModelResolver.Result.Inherit] does the profile's own model get a chance.
     */
    fun combinedModelResolution(
        modelResolution: SubAgentModelResolver.Result,
        profile: SubAgentProfile?,
    ): SubAgentModelResolver.Result = when (modelResolution) {
        is SubAgentModelResolver.Result.Inherit -> profile?.modelId?.let {
            SubAgentModelResolver.Result.Resolved(it.toString())
        } ?: modelResolution

        else -> modelResolution
    }
}
