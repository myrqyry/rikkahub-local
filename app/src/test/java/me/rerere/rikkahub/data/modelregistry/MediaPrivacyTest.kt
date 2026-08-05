package me.rerere.rikkahub.data.modelregistry

import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPrivacyTest {
    @Test
    fun cloudAttachmentIsBlockedWhenAssistantDisallowsIt() {
        val provider = ProviderSetting.OpenAI()

        assertFalse(Assistant(allowCloudAttachmentProcessing = false).canProcessAttachmentWith(provider))
        assertFalse(Assistant(allowCloudImageProcessing = false).canProcessImageWith(provider))
    }

    @Test
    fun onDeviceProvidersRemainAvailableWhenCloudProcessingIsDisabled() {
        val provider = ProviderSetting.AICore()
        val assistant = Assistant(
            allowCloudAttachmentProcessing = false,
            allowCloudImageProcessing = false,
        )

        assertTrue(assistant.canProcessAttachmentWith(provider))
        assertTrue(assistant.canProcessImageWith(provider))
    }
}
