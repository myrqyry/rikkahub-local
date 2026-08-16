package me.rerere.rikkahub.data.datastore.migration

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 stabilization: prove a backup `settings.json` survives [SettingsJsonMigrator] with every
 * non-migrated category byte-identical, and that the migrated categories keep their data.
 *
 * The migrator only rewrites `mcpServers` and `assistants`; everything else (providers and
 * credentials, model-role assignments, privacy toggles, TTS/ASR selections, unknown future
 * fields) must pass through untouched. Any future migration must stay a narrow rewrite, not a
 * re-serialization that drops unknown keys.
 */
class SettingsJsonMigratorPreservationTest {

    private val settingsJson = """
        {
          "providers": [
            {"id":"openai","name":"OpenAI","type":"openai","apiKey":"sk-secret-openai","models":[{"id":"gpt-4o","name":"GPT-4o"}]},
            {"id":"claude","name":"Claude","type":"anthropic","apiKey":"sk-secret-claude"},
            {"id":"liteRt","name":"LiteRT","type":"litert"}
          ],
          "deleted_builtin_provider_ids": ["local_dream"],
          "assistants": [
            {
              "id":"a1","name":"Assistant One",
              "modelRoleOverrides":{"chat":{"providerId":"claude","modelId":"claude-sonnet"}},
              "presetMessages":[{"role":"user","parts":[{"type":"me.rerere.ai.ui.UIMessagePart.Text","text":"hi"}]}]
            },
            {"id":"a2","name":"Assistant Two"}
          ],
          "modelRoles":{
            "chat":{"providerId":"openai","modelId":"gpt-4o"},
            "vision":{"providerId":"liteRt","modelId":"gemma-vision"},
            "ocr":{"providerId":"liteRt","modelId":"gemma-vision"},
            "embedding":{"providerId":"openai","modelId":"text-embedding-3-small"}
          },
          "privacy":{
            "allowCloud":false,
            "allowCloudImageGeneration":false,
            "requireLocalForVision":true
          },
          "selectedTtsProvider":"pocket",
          "selectedAsrProvider":"whisper",
          "ttsProviders":[{"id":"pocket","name":"Pocket"}],
          "asrProviders":[{"id":"whisper","name":"Whisper"}],
          "mcpServers":[{"type":"me.rerere.rikkahub.data.mcp.McpServerConfig.SseTransportServer","name":"m1"}],
          "quickMessages":[{"id":"qm1","title":"Hi","content":"Hello"}],
          "futureSettingZ":{"enabled":true,"payload":{"nested":1}}
        }
    """.trimIndent()

    @Test
    fun `migration preserves every non-migrated category byte-for-byte`() {
        val out = JsonInstant.parseToJsonElement(SettingsJsonMigrator.migrate(settingsJson)).jsonObject
        val original = JsonInstant.parseToJsonElement(settingsJson).jsonObject

        // Categories the migrator must never touch: providers (incl. credentials), role
        // assignments, privacy, TTS/ASR, deleted-provider list, quickMessages, unknown fields.
        val passthroughKeys = listOf(
            "providers", "deleted_builtin_provider_ids", "modelRoles", "privacy",
            "selectedTtsProvider", "selectedAsrProvider", "ttsProviders", "asrProviders",
            "quickMessages", "futureSettingZ",
        )
        for (key in passthroughKeys) {
            assertEquals("key $key must pass through unchanged", original[key], out[key])
        }
    }

    @Test
    fun `assistants survive and keep their data after type rewrite`() {
        val out = JsonInstant.parseToJsonElement(SettingsJsonMigrator.migrate(settingsJson)).jsonObject
        val assistants = out["assistants"]!!.jsonArray
        assertEquals(2, assistants.size)
        val a1 = assistants[0].jsonObject
        assertEquals("a1", a1["id"]!!.jsonPrimitive.content)
        assertEquals("Assistant One", a1["name"]!!.jsonPrimitive.content)
        // model-role override survives
        assertEquals(
            "claude",
            a1["modelRoleOverrides"]!!.jsonObject["chat"]!!.jsonObject["providerId"]!!.jsonPrimitive.content,
        )
        // preset part type rewritten, payload preserved
        val part = a1["presetMessages"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject
        assertEquals("text", part["type"]!!.jsonPrimitive.content)
        assertEquals("hi", part["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `mcp servers keep data after type rewrite`() {
        val out = JsonInstant.parseToJsonElement(SettingsJsonMigrator.migrate(settingsJson)).jsonObject
        val server = out["mcpServers"]!!.jsonArray[0].jsonObject
        assertEquals("sse", server["type"]!!.jsonPrimitive.content)
        assertEquals("m1", server["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `unknown future fields are ignored not dropped`() {
        val out = JsonInstant.parseToJsonElement(SettingsJsonMigrator.migrate(settingsJson)).jsonObject
        assertTrue(out.containsKey("futureSettingZ"))
        assertEquals(
            1,
            out["futureSettingZ"]!!.jsonObject["payload"]!!.jsonObject["nested"]!!.jsonPrimitive.content.toInt(),
        )
    }
}
