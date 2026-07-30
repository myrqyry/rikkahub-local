package me.rerere.rikkahub.appfunctions

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionCancelledException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "RikkaAppFunctionService",
    appFunctionXmlFileName = "rikka_app_function_service",
)
abstract class BaseRikkaAppFunctionService : AppFunctionService() {
    private val chatService: ChatService by inject()
    private val conversationRepo: ConversationRepository by inject()
    private val settingsStore: SettingsStore by inject()
    private val appScope: AppScope by inject()

    @AppFunction(isDescribedByKDoc = true)
    suspend fun sendChatMessage(
        conversationId: String,
        message: String,
    ): String = withContext(Dispatchers.IO) {
        val uuid = Uuid.parse(conversationId)
        chatService.initializeConversation(uuid)
        val response = CompletableDeferred<String>()
        val collectJob = appScope.launch {
            chatService.generationDoneFlow.first { it == uuid }
            val conv = conversationRepo.getConversationById(uuid)
            val text = conv?.messageNodes
                ?.filter { it.role == MessageRole.ASSISTANT }
                ?.lastOrNull()
                ?.currentMessage
                ?.parts
                ?.filterIsInstance<UIMessagePart.Text>()
                ?.joinToString("") { it.text }
                ?: ""
            response.complete(text)
        }
        try {
            chatService.sendMessage(uuid, listOf(UIMessagePart.Text(message)), answer = true)
            withTimeout(120_000L) { response.await() }
        } catch (e: TimeoutCancellationException) {
            throw AppFunctionCancelledException("Message generation timed out after 120 seconds")
        } finally {
            collectJob.cancel()
        }
    }

    @AppFunction(isDescribedByKDoc = true)
    suspend fun listConversations(
        limit: Int = 20,
    ): List<ConversationSummary> = withContext(Dispatchers.IO) {
        val settings = settingsStore.settingsFlowRaw.first()
        val assistantId = settings.getCurrentAssistant().id
        conversationRepo.getRecentConversations(assistantId, limit).map { conv ->
            ConversationSummary(
                id = conv.id.toString(),
                title = conv.title,
            )
        }
    }
}

@AppFunctionSerializable(isDescribedByKDoc = true)
data class ConversationSummary(
    val id: String,
    val title: String,
)
