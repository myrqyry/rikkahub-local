package me.rerere.locallm.llamacpp

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.ggufTuningDataStore by preferencesDataStore(name = "gguf_tuning_v1")

/**
 * Separate and opt-in: never changes global defaults or any other inference backend.
 * A changed model file, Android build, or Llamatik ABI invalidates the applied record.
 */
class GgufTuningStore(context: Context) {
    private val store = context.applicationContext.ggufTuningDataStore
    private val key = stringPreferencesKey("model_runs")
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(raw: String?): Map<String, GgufTuningRecord> =
        if (raw == null) emptyMap() else
            json.decodeFromString<Map<String, GgufTuningRecord>>(raw)

    fun records(): Flow<Map<String, GgufTuningRecord>> =
        store.data.map { decode(it[key]) }

    suspend fun selected(modelId: String, file: File): GgufTuningRecord? {
        if (!file.isFile) return null
        val record = records().first()[modelId] ?: return null
        return record.takeIf { it.fingerprint == GgufTuningPolicy.fingerprint(file) }
    }

    suspend fun applied(modelId: String, file: File): GgufTuningConfig? =
        selected(modelId, file)?.takeIf { it.applied }?.winner?.config

    suspend fun saveMeasurement(modelId: String, record: GgufTuningRecord) {
        require(modelId.isNotBlank())
        require(!record.applied)
        store.edit { prefs ->
            val current = decode(prefs[key]).toMutableMap()
            current[modelId] = record
            prefs[key] = json.encodeToString(current)
        }
    }

    /** Removes only the named model's evidence. Useful when a model is deleted. */
    suspend fun deleteMeasurement(modelId: String) {
        store.edit { prefs ->
            val current = decode(prefs[key]).toMutableMap()
            current.remove(modelId)
            prefs[key] = json.encodeToString(current)
        }
    }

    suspend fun setApplied(modelId: String, file: File, apply: Boolean) {
        val currentFingerprint = GgufTuningPolicy.fingerprint(file)
        store.edit { prefs ->
            val current = decode(prefs[key]).toMutableMap()
            val saved = current[modelId]
            require(saved != null && saved.fingerprint == currentFingerprint) {
                "GGUF model has changed; retune before applying"
            }
            current[modelId] = saved.copy(applied = apply)
            prefs[key] = json.encodeToString(current)
        }
    }
}
