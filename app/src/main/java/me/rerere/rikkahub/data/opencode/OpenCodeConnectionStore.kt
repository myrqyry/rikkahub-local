package me.rerere.rikkahub.data.opencode

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.OpenCodeConnectionDAO
import me.rerere.rikkahub.data.db.dao.OpenCodeWorkspaceRefDAO
import me.rerere.rikkahub.data.db.entity.OpenCodeConnectionEntity
import me.rerere.rikkahub.data.db.entity.OpenCodeWorkspaceRefEntity

interface OpenCodeConnectionStore {
    val connections: Flow<List<OpenCodeConnectionEntity>>
    val workspaceRefs: Flow<List<OpenCodeWorkspaceRefEntity>>
    suspend fun getConnection(id: String): OpenCodeConnectionEntity?
    suspend fun getWorkspaceRef(id: String): OpenCodeWorkspaceRefEntity?
    suspend fun saveConnection(connection: OpenCodeConnectionEntity)
    suspend fun saveWorkspaceRef(ref: OpenCodeWorkspaceRefEntity)
    suspend fun getSecret(ref: String): String?
    suspend fun saveSecret(ref: String, value: String)
    suspend fun deleteConnection(id: String)
    suspend fun deleteWorkspaceRef(id: String)
}

class RoomOpenCodeConnectionStore(
    private val connectionsDao: OpenCodeConnectionDAO,
    private val workspaceRefsDao: OpenCodeWorkspaceRefDAO,
    private val secretStore: OpenCodeSecretStore,
) : OpenCodeConnectionStore {
    override val connections: Flow<List<OpenCodeConnectionEntity>> = connectionsDao.listFlow()
    override val workspaceRefs: Flow<List<OpenCodeWorkspaceRefEntity>> = workspaceRefsDao.listFlow()

    override suspend fun getConnection(id: String) = connectionsDao.getById(id)
    override suspend fun getWorkspaceRef(id: String) = workspaceRefsDao.getById(id)
    override suspend fun saveConnection(connection: OpenCodeConnectionEntity) = connectionsDao.upsert(connection)
    override suspend fun saveWorkspaceRef(ref: OpenCodeWorkspaceRefEntity) = workspaceRefsDao.upsert(ref)
    override suspend fun getSecret(ref: String) = secretStore.get(ref)
    override suspend fun saveSecret(ref: String, value: String) = secretStore.put(ref, value)

    override suspend fun deleteConnection(id: String) {
        connectionsDao.getById(id)?.authSecretRef?.let { secretStore.delete(it) }
        connectionsDao.deleteById(id)
    }

    override suspend fun deleteWorkspaceRef(id: String) {
        workspaceRefsDao.deleteById(id)
    }
}
