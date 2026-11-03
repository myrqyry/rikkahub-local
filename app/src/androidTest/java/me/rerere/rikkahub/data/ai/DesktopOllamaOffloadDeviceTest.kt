package me.rerere.rikkahub.data.ai

import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Manual opt-in real phone-to-desktop inference acceptance check.
 * Requires configured Tailscale and an installed desktop Ollama model.
 * No model downloads, agent/tool calls, chats, or settings changes.
 * ONLY run manually via am instrument -w -e tailnetInference enabled
 * -e class me.rerere.rikkahub.data.ai.DesktopOllamaOffloadDeviceTest
 * excp.rikkahub.local.debug.test/androidx.test.runner.AndroidJUnitRunner
 *
 * NEVER use Gradle connectedDebugAndroidTest on a phone with valuable data;
 * that runner can uninstall the target app during cleanup.
 */
@RunWith(AndroidJUnit4::class)
class DesktopOllamaOffloadDeviceTest {
    @Test
    fun configuredTailscaleOpenAiProviderGeneratesDesktopTokens() = runBlocking {
        assumeTrue(
            "Live external inference is opt-in",
            InstrumentationRegistry.getArguments().getString("tailnetInference") == "enabled",
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(
            "Only run on debuggable RikkaHub",
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
        val settingsStore = GlobalContext.get().get<SettingsStore>()
        val providerManager = GlobalContext.get().get<ProviderManager>()
        val provider = settingsStore.settingsFlow.first().providers
            .filterIsInstance<ProviderSetting.OpenAI>()
            .single { it.name == "tailscale" && it.enabled }
        val model = provider.models.single { it.modelId == "granite4.1:3b" }
        val began = SystemClock.elapsedRealtime()
        val response = withTimeout(120_000) {
            providerManager.getProviderByType(provider).generateText(
                provider,
                listOf(
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Text("Reply with only the word willow.")),
                    ),
                ),
                TextGenerationParams(model = model, maxTokens = 20, tools = emptyList()),
            )
        }
        val text = response.choices.firstOrNull()?.message?.parts
            .orEmpty().filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
        Log.i(
            "DesktopOllamaOffload",
            "remote_provider=OpenAI desktop_model=" + model.modelId +
                " text=" + text.take(80) +
                " elapsed_ms=" + (SystemClock.elapsedRealtime() - began),
        )
        assertTrue("Expected live desktop response, got '$text'", text.contains("willow", ignoreCase = true))
    }
}
