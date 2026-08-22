package me.rerere.agentruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolResultContextDispositionTest {
    @Test
    fun `small text stays inline`() {
        val candidate = candidate(byteSize = 4)

        assertEquals(
            ToolResultDisposition.INLINE,
            DeterministicToolResultContextPolicy(maxInlineBytes = 8).decide(candidate),
        )
    }

    @Test
    fun `oversized text reduces deterministically`() {
        val candidate = candidate(byteSize = 9)

        assertEquals(
            ToolResultDisposition.REDUCE,
            DeterministicToolResultContextPolicy(maxInlineBytes = 8).decide(candidate),
        )
        assertEquals(
            "hello...",
            ToolResultContextProcessor.project(
                ToolResultDisposition.REDUCE,
                "hello world",
                candidate,
                maxInlineBytes = 8,
            ).text,
        )
    }

    @Test
    fun `exact index request falls back to a durable reference`() {
        val candidate = candidate(byteSize = 100, evidenceId = "evidence-7")

        assertEquals(
            ToolResultDisposition.REFERENCE_ONLY,
            ToolResultContextProcessor.effectiveDisposition(
                ToolResultDisposition.INDEX_EXACT,
                candidate,
            ),
        )
    }

    @Test
    fun `discard leaves evidence untouched and projects no text`() {
        assertNull(
            ToolResultContextProcessor.project(
                ToolResultDisposition.DISCARD,
                "raw result",
                candidate(byteSize = 100, evidenceId = "evidence-8"),
                maxInlineBytes = 8,
            ).text,
        )
    }

    private fun candidate(byteSize: Int, evidenceId: String = "e1") = ToolResultContextCandidate(
        toolName = "read_file",
        contentType = "text/plain",
        byteSize = byteSize,
        structured = false,
        evidenceId = evidenceId,
        exactRetrievable = false,
    )
}
