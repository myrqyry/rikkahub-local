package me.rerere.rikkahub.data.opencode

import kotlinx.coroutines.flow.Flow

interface OpenCodeClient {
    suspend fun health(): OpenCodeHealth
    suspend fun listProjects(): List<OpenCodeProject>
    suspend fun createSession(directory: String?): OpenCodeSession
    suspend fun listSessions(directory: String?): List<OpenCodeSession>
    suspend fun getSession(sessionId: String, directory: String?): OpenCodeSession
    suspend fun getMessages(sessionId: String, directory: String?): List<OpenCodeMessage>
    suspend fun prompt(sessionId: String, directory: String?, text: String): OpenCodeMessage
    suspend fun abortSession(sessionId: String, directory: String?): Boolean
    fun observeSession(sessionId: String, directory: String?): Flow<OpenCodeSessionSignal>
    suspend fun getSessionStatus(sessionId: String, directory: String?): String?
    suspend fun listPendingPermissions(sessionId: String, directory: String?): List<OpenCodePermission>
    suspend fun replyPermission(sessionId: String, directory: String?, requestId: String, reply: OpenCodePermissionReply): Boolean
}
