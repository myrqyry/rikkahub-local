package me.rerere.rikkahub.ui.pages.models.components

import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class ManageSourcesSheetTest {
    @Test
    fun `source list keeps enabled and user configured providers only`() {
        val providers = listOf(
            ProviderSetting.LiteRtLocal(enabled = true),
            ProviderSetting.StableDiffusion(enabled = false),
            ProviderSetting.OpenAI(enabled = false, builtIn = false),
        )

        assertEquals(
            listOf("Local · LiteRT", "OpenAI"),
            configuredProviders(providers).map { it.name },
        )
    }
}
