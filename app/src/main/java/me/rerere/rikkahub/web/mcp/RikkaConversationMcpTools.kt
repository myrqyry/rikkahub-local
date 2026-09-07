package me.rerere.rikkahub.web.mcp

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.InputSchema
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.web.dto.ConversationDto
import me.rerere.rikkahub.web.dto.ConversationListDto
import me.rerere.rikkahub.web.dto.toDto
import me.rerere.rikkahub.web.dto.toListDto
import kotlin.uuid.Uuid

private const val DEFAULT_LIMIT = 50
private const val MAX_LIMIT = 100

class RikkaConversationMcpTools(
    private val conversationRepository: ConversationRepository,
    private val settingsStore: SettingsStore,
) {
    fun tools(): List<NativeMcpTool> = listOf(
        NativeMcpTool(
            name = "rikka.list_conversations",
            description = "List persisted conversations for the current RikkaHub assistant.",
            inputSchema = InputSchema.Obj(buildJsonObject {
                put("limit", buildJsonObject { put("type", "integer") })
            }),
            call = ::listConversations,
        ),
        NativeMcpTool(
            name = "rikka.get_conversation",
            description = "Retrieve one persisted RikkaHub conversation by id.",
            inputSchema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject { put("type", "string") })
                },
                required = listOf("id"),
            ),
            call = ::getConversation,
        ),
    )

    private suspend fun listConversations(arguments: JsonObject): JsonObject {
        val limit = arguments["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: DEFAULT_LIMIT
        if (limit !in 1..MAX_LIMIT) {
            throw NativeMcpInvalidParamsException("limit must be between 1 and $MAX_LIMIT")
        }

        val assistantId = settingsStore.settingsFlow.first().assistantId
        val conversations = conversationRepository
            .getConversationsOfAssistant(assistantId)
            .first()
        val items = conversations.take(limit).map { it.toListDto() }
        return buildJsonObject {
            put("assistantId", assistantId.toString())
            put("conversations", JsonArray(items.map(::encodeListDto)))
            put("hasMore", conversations.size > limit)
        }
    }

    private suspend fun getConversation(arguments: JsonObject): JsonObject {
        val id = arguments["id"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
            ?: throw NativeMcpInvalidParamsException("id is required")
        val conversationId = runCatching { Uuid.parse(id) }.getOrNull()
            ?: throw NativeMcpInvalidParamsException("id must be a valid UUID")
        val conversation = conversationRepository.getConversationById(conversationId)
            ?: throw NativeMcpInvalidParamsException("Conversation not found")
        return buildJsonObject {
            put("conversation", encodeDto(conversation.toDto()))
        }
    }

    private fun encodeListDto(dto: ConversationListDto) =
        JsonInstant.encodeToJsonElement(ConversationListDto.serializer(), dto)

    private fun encodeDto(dto: ConversationDto) =
        JsonInstant.encodeToJsonElement(ConversationDto.serializer(), dto)
}
