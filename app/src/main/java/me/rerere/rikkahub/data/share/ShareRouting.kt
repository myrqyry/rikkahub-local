package me.rerere.rikkahub.data.share

import me.rerere.rikkahub.skills.imports.ArtifactKind
import me.rerere.rikkahub.skills.imports.ArtifactSourceKind
import me.rerere.rikkahub.skills.imports.ImportRequest

data class ComposerDraftContent(val initText: String? = null, val initFiles: List<String>? = null)

sealed interface ShareRoutingDecision {
    data class ImportCandidate(val request: ImportRequest) : ShareRoutingDecision
    data class ComposerDraft(val draft: ComposerDraftContent) : ShareRoutingDecision
    data class Unsupported(val reason: String) : ShareRoutingDecision
}

object ArtifactImportRecognizer {
    private val skillUrlRegex = Regex("""https?://(github\.com|raw\.githubusercontent\.com)/.+""")

    fun recognize(payload: InboundSharePayload): ShareRoutingDecision = when (payload) {
        is InboundSharePayload.Text -> ShareRoutingDecision.ComposerDraft(ComposerDraftContent(initText = payload.text))
        is InboundSharePayload.Url -> recognizeUrl(payload.url, payload.accompanyingText)
        is InboundSharePayload.File -> recognizeFile(payload)
    }

    private fun recognizeUrl(url: String, accompanyingText: String?): ShareRoutingDecision {
        val lower = url.lowercase()
        val looksLikeSkill = skillUrlRegex.matches(url) && lower.contains("skill")
        val looksLikePlugin = lower.contains("plugin") && lower.contains("zip")
        return when {
            looksLikeSkill || looksLikePlugin -> ShareRoutingDecision.ImportCandidate(
                ImportRequest(
                    source = url,
                    expectedKind = if (looksLikeSkill) ArtifactKind.SKILL else ArtifactKind.PLUGIN,
                    sourceKind = ArtifactSourceKind.GITHUB,
                )
            )
            else -> ShareRoutingDecision.ComposerDraft(ComposerDraftContent(initText = url, initFiles = accompanyingText?.let { listOf(it) }))
        }
    }

    private fun recognizeFile(payload: InboundSharePayload.File): ShareRoutingDecision =
        classifyFile(payload.uri.toString(), payload.mimeType.orEmpty(), payload.displayName.orEmpty(), payload.accompanyingText)

    internal fun classifyFile(uri: String, mime: String, displayName: String, accompanyingText: String?): ShareRoutingDecision {
        val name = displayName.lowercase()
        val normalizedMime = mime.lowercase()
        return when {
            name.endsWith(".skill.md") || name.endsWith(".plugin.zip") || normalizedMime.contains("x-skill") ->
                ShareRoutingDecision.ImportCandidate(
                    ImportRequest(
                        source = uri,
                        expectedKind = if (name.endsWith(".plugin.zip")) ArtifactKind.PLUGIN else ArtifactKind.SKILL,
                        sourceKind = ArtifactSourceKind.LOCAL_FILE,
                    )
                )
            else -> ShareRoutingDecision.ComposerDraft(ComposerDraftContent(initText = accompanyingText, initFiles = listOf(uri)))
        }
    }
}
