package me.rerere.rikkahub.data.share

import me.rerere.rikkahub.skills.imports.ArtifactSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareRoutingTest {

    @Test
    fun `skill url routes to import`() {
        val decision = ArtifactImportRecognizer.recognize(
            InboundSharePayload.Url("https://github.com/acme/skill-weather")
        )
        assertTrue(decision is ShareRoutingDecision.ImportCandidate)
        val request = (decision as ShareRoutingDecision.ImportCandidate).request
        assertEquals(ArtifactSourceKind.GITHUB, request.sourceKind)
    }

    @Test
    fun `ordinary url routes to composer draft`() {
        val url = "https://example.com/article"
        val decision = ArtifactImportRecognizer.recognize(InboundSharePayload.Url(url))
        assertTrue(decision is ShareRoutingDecision.ComposerDraft)
        val draft = (decision as ShareRoutingDecision.ComposerDraft).draft
        assertEquals(url, draft.initText)
    }

    @Test
    fun `plain text routes to composer draft`() {
        val decision = ArtifactImportRecognizer.recognize(InboundSharePayload.Text("just a note"))
        assertTrue(decision is ShareRoutingDecision.ComposerDraft)
        val draft = (decision as ShareRoutingDecision.ComposerDraft).draft
        assertEquals("just a note", draft.initText)
    }

    @Test
    fun `plugin file routes to import`() {
        val decision = ArtifactImportRecognizer.classifyFile(
            "content://x/plugin.plugin.zip", "application/octet-stream", "plugin.plugin.zip", null
        )
        assertTrue(decision is ShareRoutingDecision.ImportCandidate)
        val request = (decision as ShareRoutingDecision.ImportCandidate).request
        assertTrue(request.source.endsWith(".plugin.zip"))
    }

    @Test
    fun `image file routes to composer draft with attachment`() {
        val decision = ArtifactImportRecognizer.classifyFile(
            "content://x/1.png", "image/png", "1.png", null
        )
        assertTrue(decision is ShareRoutingDecision.ComposerDraft)
        val draft = (decision as ShareRoutingDecision.ComposerDraft).draft
        assertEquals(listOf("content://x/1.png"), draft.initFiles)
    }
}
