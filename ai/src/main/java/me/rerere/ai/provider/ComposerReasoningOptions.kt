package me.rerere.ai.provider

import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.registry.ModelRegistry
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Reasoning choices for the currently selected model.
 *
 * When no per-model control metadata is known, keep the legacy choices rather than
 * inventing restrictions. When controls are known, show only model-advertised efforts;
 * AUTO remains available because it does not request an explicit effort.
 *
 * A budget-only model may support adjustable token budgets, but this composer uses
 * named efforts. Don't misrepresent unverified token-budget compatibility as a level.
 */
fun Model.composerReasoningLevels(provider: ProviderSetting? = null): List<ReasoningLevel> {
    if (ModelAbility.REASONING !in abilities) return emptyList()
    if (reasoningControlTypes.isEmpty() && reasoningEfforts.isEmpty()) {
        // A catalog entry that reports reasoning but no control contract cannot
        // justify offering arbitrary efforts. Older manually configured models
        // retain the legacy picker until their capabilities can be resolved.
        return if (metadataSource?.startsWith("models.dev:") == true) {
            listOf(ReasoningLevel.AUTO)
        } else ReasoningLevel.entries
    }

    val efforts = reasoningEfforts.map { it.lowercase() }.toSet()
    val advertisesDisable = "none" in efforts || "toggle" in reasoningControlTypes
    val canDisable = advertisesDisable && providerSupportsReasoningOff(provider, efforts)

    return ReasoningLevel.entries.filter { level ->
        when (level) {
            ReasoningLevel.OFF -> canDisable
            ReasoningLevel.AUTO -> true
            ReasoningLevel.LOW -> "low" in efforts
            ReasoningLevel.MEDIUM -> "medium" in efforts
            ReasoningLevel.HIGH -> "high" in efforts
            ReasoningLevel.XHIGH -> "xhigh" in efforts || "max" in efforts
        }
    }
}

/**
 * A per-model *effective* selection, leaving the assistant's stored preference intact.
 * Shared by the composer and generation so a hidden/unsupported option is never sent
 * just because it was selected on an earlier model.
 */
fun Model.effectiveComposerReasoningLevel(
    saved: ReasoningLevel,
    provider: ProviderSetting? = null,
): ReasoningLevel {
    val available = composerReasoningLevels(provider)
    if (available.isEmpty()) return ReasoningLevel.OFF
    if (saved in available) return saved

    if (saved == ReasoningLevel.AUTO || saved == ReasoningLevel.OFF) return ReasoningLevel.AUTO

    return available.filter {
        it != ReasoningLevel.OFF && it != ReasoningLevel.AUTO &&
            it.ordinal <= saved.ordinal
    }.maxByOrNull { it.ordinal } ?: ReasoningLevel.AUTO
}

/**
 * A catalog-advertised OFF/toggle is only actionable when the selected provider's
 * request builder knows how to express a genuinely disabled reasoning mode.
 * In particular, Gemini 3 MINIMAL is not OFF and generic OpenAI-compatible
 * hosts may translate OFF to a positive effort, so neither advertises OFF here.
 */
private fun Model.providerSupportsReasoningOff(
    provider: ProviderSetting?,
    efforts: Set<String>,
): Boolean = when (provider) {
    is ProviderSetting.Claude -> true // thinking.type = disabled
    is ProviderSetting.Google ->
        !ModelRegistry.GEMINI_3_SERIES.match(modelId) &&
            !Regex("2\\.5.*pro", RegexOption.IGNORE_CASE).containsMatchIn(modelId)
    is ProviderSetting.OpenAI -> {
        when (provider.baseUrl.toHttpUrlOrNull()?.host) {
            "api.openai.com" -> "none" in efforts // reasoning_effort = none, not "low"
            "openrouter.ai", "dashscope.aliyuncs.com",
            "ark.cn-beijing.volces.com", "api.deepseek.com",
            "api.moonshot.cn", "open.bigmodel.cn",
            "aiping.cn", "chat.intern-ai.org.cn" -> true
            else -> false
        }
    }
    else -> false
}
