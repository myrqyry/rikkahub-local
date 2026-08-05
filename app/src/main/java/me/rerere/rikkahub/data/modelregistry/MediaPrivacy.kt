package me.rerere.rikkahub.data.modelregistry

import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.model.Assistant

fun ProviderSetting.isOnDevice(): Boolean = when (this) {
    is ProviderSetting.AICore,
    is ProviderSetting.LiteRtLocal,
    is ProviderSetting.StableDiffusion,
    -> true
    else -> false
}

fun Assistant.canProcessAttachmentWith(provider: ProviderSetting): Boolean =
    allowCloudAttachmentProcessing || provider.isOnDevice()

fun Assistant.canProcessImageWith(provider: ProviderSetting): Boolean =
    allowCloudImageProcessing || provider.isOnDevice()
