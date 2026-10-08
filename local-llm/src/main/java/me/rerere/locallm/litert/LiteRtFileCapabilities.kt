package me.rerere.locallm.litert

import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.ModelType
import com.google.ai.edge.litertlm.Modality as BundleModality

/**
 * Evidence declared *inside* a .litertlm bundle, independent of catalog guesses,
 * device accelerator admission and observed inference success.
 *
 * Inspection is lazy: only invoked when loading a selected local model. A null
 * result means inspection was unavailable, NOT proof of absent capabilities.
 */
internal data class LiteRtFileCapabilities(
    val kind: Kind,
    val text: Boolean,
    val vision: Boolean,
    val audio: Boolean,
    val video: Boolean,
    val thinking: Boolean? = null,
    val toolCalling: Boolean? = null,
    val speculativeDecoding: Boolean? = null,
    val maxContextTokens: Int? = null,
    val minRuntimeVersion: String? = null,
    val textBackends: List<String> = emptyList(),
) {
    enum class Kind { LLM, EMBEDDING, UNKNOWN }

    /** Do not infer that a backend *works* from a bundle declaring support for it. */
    fun executionPlan(
        requestedImage: Boolean,
        requestedAudio: Boolean,
        requestedMaxNumTokens: Int,
    ): ExecutionPlan {
        require(requestedMaxNumTokens > 0)
        if (kind == Kind.EMBEDDING) {
            throw IllegalArgumentException(
                "This .litertlm file is an embedding model, not a chat model. " +
                    "Select it for RAG/embeddings instead of chat."
            )
        }
        // UNKNOWN bundles keep the existing curated fallback, rather than
        // treating missing metadata as an affirmative denial of capabilities.
        val enforceModalities = kind == Kind.LLM
        return ExecutionPlan(
            image = requestedImage && (!enforceModalities || vision),
            audio = requestedAudio && (!enforceModalities || audio),
            maxNumTokens = maxContextTokens?.takeIf { it > 0 }
                ?.let { minOf(requestedMaxNumTokens, it) }
                ?: requestedMaxNumTokens,
        )
    }

    data class ExecutionPlan(
        val image: Boolean,
        val audio: Boolean,
        val maxNumTokens: Int,
    )
}

internal object LiteRtFileIntrospector {
    fun inspect(modelPath: String): LiteRtFileCapabilities? = try {
        ModelInfo.from(modelPath).use { info ->
            val declared = info.inputModalities()
            val kind = when (info.modelType) {
                ModelType.LLM -> LiteRtFileCapabilities.Kind.LLM
                ModelType.EMBEDDING -> LiteRtFileCapabilities.Kind.EMBEDDING
                else -> LiteRtFileCapabilities.Kind.UNKNOWN
            }
            val llm = info as? ModelInfo.Llm
            LiteRtFileCapabilities(
                kind = kind,
                text = declared.text,
                vision = declared.vision,
                audio = declared.audio,
                video = declared.video,
                thinking = llm?.supportsThinking(),
                toolCalling = llm?.supportsFunctionCalling(),
                speculativeDecoding = llm?.hasSpeculativeDecodingSupport(),
                maxContextTokens = info.maxContextTokens().takeIf { it > 0 },
                minRuntimeVersion = info.minRuntimeVersion(),
                textBackends = info.supportedBackends(BundleModality.TEXT).map { it.name },
            )
        }
    } catch (e: Exception) {
        // SDK/model formats may predate ModelInfo. Keep the original catalog
        // fallback for those models; never advertise guessed data as inspected.
        android.util.Log.w("LiteRtFileIntrospector", "Bundle metadata unavailable; using catalog defaults", e)
        null
    }
}
