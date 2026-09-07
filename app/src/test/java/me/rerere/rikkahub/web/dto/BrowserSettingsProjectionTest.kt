package me.rerere.rikkahub.web.dto

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.mcp.McpCommonOptions
import me.rerere.rikkahub.data.ai.mcp.McpOAuthState
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSettingsProjectionTest {
    @Test
    fun `browser settings omit credential material from nested configuration`() {
        val settings = Settings(
            providers = listOf(ProviderSetting.OpenAI(apiKey = "SUPER_SECRET_API_KEY")),
            webDavConfig = WebDavConfig(password = "SUPER_SECRET_WEBDAV_PASSWORD"),
            s3Config = S3Config(secretAccessKey = "SUPER_SECRET_S3_KEY"),
            mcpServers = listOf(
                McpServerConfig.SseTransportServer(
                    commonOptions = McpCommonOptions(
                        headers = listOf("Authorization" to "Bearer SUPER_SECRET_HEADER_TOKEN"),
                        oauth = McpOAuthState(
                            clientSecret = "SUPER_SECRET_CLIENT_SECRET",
                            accessToken = "SUPER_SECRET_ACCESS_TOKEN",
                            refreshToken = "SUPER_SECRET_REFRESH_TOKEN",
                        ),
                    ),
                ),
            ),
            webServerAccessPassword = "SUPER_SECRET_WEB_PASSWORD",
        )

        val output = settings.toBrowserSafeJson().toString()

        assertFalse(output.contains("SUPER_SECRET"))
        assertFalse(output.contains("apiKey"))
        assertFalse(output.contains("privateKey"))
        assertFalse(output.contains("accessToken"))
        assertFalse(output.contains("refreshToken"))
        assertFalse(output.contains("clientSecret"))
        assertFalse(output.contains("webServerAccessPassword"))
        assertTrue(output.contains("webServerPasswordConfigured"))
        assertTrue(JsonInstant.parseToJsonElement(output).jsonObject.containsKey("providers"))
    }

    @Test
    fun `browser updates cannot overwrite credential material`() {
        val current = Settings(
            providers = listOf(ProviderSetting.OpenAI(apiKey = "ORIGINAL_API_KEY")),
        )
        val incoming = buildJsonObject {
            put("providers", JsonInstant.encodeToJsonElement(Settings.serializer(), current).jsonObject["providers"]!!)
            put("apiKey", "ATTACKER_API_KEY")
        }

        val merged = mergeBrowserSettings(JsonInstant.encodeToJsonElement(Settings.serializer(), current).jsonObject, incoming)

        assertTrue(merged.toString().contains("ORIGINAL_API_KEY"))
        assertFalse(merged.toString().contains("ATTACKER_API_KEY"))
    }
}
