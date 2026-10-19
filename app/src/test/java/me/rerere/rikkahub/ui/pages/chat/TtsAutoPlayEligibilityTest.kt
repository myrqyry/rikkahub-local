package me.rerere.rikkahub.ui.pages.chat

import kotlin.uuid.Uuid
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.*
import org.junit.Test

class TtsAutoPlayEligibilityTest {
    private fun chat(vararg messages: UIMessage): Conversation = Conversation(
        title = "A test conversation",
        assistantId = Uuid.random(),
        messageNodes = messages.map { it.toMessageNode() },
    )
    private fun assistant(text: String) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(text)),
    )

    @Test fun neverSpeaksAnotherChatOrDisabledAutoplay() {
        val last = assistant("""Intro "Hello" [aside]""")
        val current = chat(UIMessage.user("hi"), last)
        val options = DisplaySetting(
            autoPlayTTSAfterGeneration = true,
            ttsOnlyReadQuoted = true,
            ttsOnlyReadOutsideBrackets = true,
        )
        assertNull(eligibleTtsAutoPlay(Uuid.random(), current, options, null))
        assertNull(eligibleTtsAutoPlay(current.id, current,
            options.copy(autoPlayTTSAfterGeneration = false), null))
        val candidate = eligibleTtsAutoPlay(current.id, current, options, null)
        assertNotNull(candidate)
        assertEquals("Hello", candidate!!.text)
        assertEquals(last.id, candidate.messageId)
        assertNull(eligibleTtsAutoPlay(current.id, current, options, last.id))
    }

    @Test fun noAssistantResponseOrNoPermittedTextDoesNotAutoplay() {
        val userOnly = chat(UIMessage.user("Do not answer yet"))
        assertNull(eligibleTtsAutoPlay(userOnly.id, userOnly,
            DisplaySetting(autoPlayTTSAfterGeneration = true), null))

        val unquoted = chat(UIMessage.user("hey"), assistant("Nothing in quotes"))
        assertNull(eligibleTtsAutoPlay(unquoted.id, unquoted,
            DisplaySetting(autoPlayTTSAfterGeneration = true, ttsOnlyReadQuoted = true), null))

        val bracketOnly = chat(UIMessage.user("hey"), assistant("[only stage directions]"))
        assertNull(eligibleTtsAutoPlay(bracketOnly.id, bracketOnly,
            DisplaySetting(autoPlayTTSAfterGeneration = true, ttsOnlyReadOutsideBrackets = true), null))
    }
}
