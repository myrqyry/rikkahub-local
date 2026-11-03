package me.rerere.rikkahub.web.routes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.web.BadRequestException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInferenceContractTest {
    private fun payload(body: String) = Json.parseToJsonElement(body).jsonObject
    private fun parse(body: String) = parseLocalCompletionRequest(payload(body))

    @Test fun gatewayRefusesUnprotectedLanAndPublicClients() {
        assertTrue(isLocalInferenceAccessAllowed(false, true, "127.0.0.1"))
        assertTrue(isLocalInferenceAccessAllowed(false, true, "::1"))
        assertFalse(isLocalInferenceAccessAllowed(false, false, "127.0.0.1"))
        assertFalse(isLocalInferenceAccessAllowed(false, true, "192.168.1.24"))
        assertFalse(isLocalInferenceAccessAllowed(false, true, "100.85.12.14"))
        assertFalse(isLocalInferenceAccessAllowed(false, true, "not-an-ip.invalid"))
        assertTrue(isLocalInferenceAccessAllowed(true, false, "192.168.1.24"))
    }

    @Test fun parsesSimpleCompletionWithoutStartingAnAgent() {
        val req = parse("""{"model":"litert/local.litertlm","stream":true,"messages":[{"role":"system","content":"Be brief."},{"role":"user","content":"hi"}],"max_tokens":48}""")
        assertEquals("litert/local.litertlm", req.model)
        assertTrue(req.stream)
        assertEquals(48, req.maxTokens)
        assertEquals(MessageRole.SYSTEM, req.messages[0].role)
        assertEquals("hi", (req.messages[1].parts.first() as UIMessagePart.Text).text)
    }

    @Test fun rejectsToolExecutionAndHistoricalToolCalls() {
        for (option in listOf(
            """"tools":[{"type":"function"}]""",
            """"tool_choice":"auto"""",
            """"tool_calls":[{"id":"call_1"}]""",
            """"response_format":{"type":"json_schema"}""",
        )) {
            assertThrows(BadRequestException::class.java) {
                parse("""{"model":"litert/local.litertlm","messages":[{"role":"user","content":"hello"}],$option}""")
            }
        }
    }

    @Test fun rejectsImageMessagesAndUnboundedHistory() {
        assertThrows(BadRequestException::class.java) {
            parse("""{"model":"litert/local.litertlm","messages":[{"role":"user","content":[{"type":"image_url"}]}]}""")
        }
        val oversized = "x".repeat(24_001)
        assertThrows(BadRequestException::class.java) {
            parse("""{"model":"litert/local.litertlm","messages":[{"role":"user","content":"$oversized"}]}""")
        }
    }

    @Test fun validatesOptionsAndSingleChoice() {
        for (option in listOf(
            """"stream":"sure"""",
            """"n":2""",
            """"temperature":99""",
            """"top_p":-1""",
            """"max_completion_tokens":9999""",
        )) {
            assertThrows(BadRequestException::class.java) {
                parse("""{"model":"litert/m","messages":[{"role":"user","content":"hello"}],$option}""")
            }
        }
    }

    @Test fun completionResponsesHaveOpenAiCompatibleShapes() {
        val response = completionResponse("id", "litert/m", 123, "hello")
        assertEquals("chat.completion", response["object"]?.jsonPrimitive?.content)
        val choice = response["choices"]!!.jsonArray.single().jsonObject
        assertEquals("hello", choice["message"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("stop", choice["finish_reason"]!!.jsonPrimitive.content)
        val start = completionDelta("id", "litert/m", 123, roleOnly = true)
        assertEquals("assistant", start["choices"]!!.jsonArray.single().jsonObject["delta"]!!.jsonObject["role"]!!.jsonPrimitive.content)
        val token = completionDelta("id", "litert/m", 123, content = "abc")
        assertEquals("abc", token["choices"]!!.jsonArray.single().jsonObject["delta"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        val end = completionDelta("id", "litert/m", 123, finish = "stop")
        assertEquals("stop", end["choices"]!!.jsonArray.single().jsonObject["finish_reason"]!!.jsonPrimitive.content)
    }

    @Test fun acceptsDeveloperInstructionsAsSystemButNotToolRoles() {
        val request = parse("""{"model":"litert/m","messages":[{"role":"developer","content":"No files"},{"role":"user","content":"Hi"}]}""")
        assertEquals(MessageRole.SYSTEM, request.messages.first().role)
        assertThrows(BadRequestException::class.java) {
            parse("""{"model":"litert/m","messages":[{"role":"tool","content":"unsafe"}]}""")
        }
    }
}
