package me.rerere.locallm.litert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptPrefixKeyTest {

    private val baseTurns = listOf(
        Turn(ROLE_USER, "hello"),
        Turn(ROLE_ASSISTANT, "hi there"),
    )

    private fun key(
        modelId: String = "model-a",
        modelRevision: String = "rev-1",
        tokenizerRevision: String = "tok-1",
        runtimeRevision: String? = "rt-1",
        conversationId: String = "conv-1",
        branchId: String = "branch-1",
        turns: List<Turn> = baseTurns,
        cachedTokens: Long? = null,
    ): PromptPrefixKey = PromptPrefixKey.of(
        modelId, modelRevision, tokenizerRevision, runtimeRevision,
        conversationId, branchId, turns, cachedTokens,
    )

    @Test
    fun identicalKeyReuses() {
        assertTrue(key().canReuse(key()))
    }

    @Test
    fun nullRetainedNeverReuses() {
        assertFalse(key().canReuse(null))
    }

    @Test
    fun anyIdentityFieldMismatchInvalidatesReuse() {
        val base = key()
        assertFalse("modelId", key(modelId = "other").canReuse(base))
        assertFalse("modelRevision", key(modelRevision = "rev-2").canReuse(base))
        assertFalse("tokenizerRevision", key(tokenizerRevision = "tok-2").canReuse(base))
        assertFalse("runtimeRevision null", key(runtimeRevision = null).canReuse(base))
        assertFalse("runtimeRevision diff", key(runtimeRevision = "rt-2").canReuse(base))
        assertFalse("conversationId", key(conversationId = "conv-2").canReuse(base))
        assertFalse("branchId", key(branchId = "branch-2").canReuse(base))
    }

    @Test
    fun editedTurnChangesPrefixHashAndInvalidatesReuse() {
        val edited = key(turns = listOf(Turn(ROLE_USER, "hello"), Turn(ROLE_ASSISTANT, "changed")))
        val base = key()
        assertFalse(edited.prefixHash == base.prefixHash)
        assertFalse(edited.canReuse(base))
    }

    @Test
    fun reorderedTurnsChangePrefixHash() {
        val forward = key(turns = listOf(Turn(ROLE_USER, "a"), Turn(ROLE_ASSISTANT, "b")))
        val backward = key(turns = listOf(Turn(ROLE_ASSISTANT, "b"), Turn(ROLE_USER, "a")))
        assertFalse(forward.prefixHash == backward.prefixHash)
        assertFalse(forward.canReuse(backward))
    }

    @Test
    fun ofIsDeterministicAndCarriesTokenCount() {
        val a = key(cachedTokens = 1234L)
        val b = key(cachedTokens = 1234L)
        assertEquals(a, b)
        assertEquals(a.prefixHash, b.prefixHash)
        assertEquals(1234L, a.cachedPromptTokenCount)
    }
}
