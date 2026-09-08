package me.rerere.rikkahub.data.opencode

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

interface OpenCodeSecretStore {
    suspend fun get(ref: String): String?
    suspend fun put(ref: String, value: String)
    suspend fun delete(ref: String)
}

class AndroidOpenCodeSecretStore(context: Context, private val json: Json) : OpenCodeSecretStore {
    private val file = File(context.noBackupFilesDir, FILE_NAME)

    override suspend fun get(ref: String): String? = readState().values[ref]

    override suspend fun put(ref: String, value: String) {
        val state = readState().values.toMutableMap().apply { this[ref] = value }
        writeState(SecretState(state))
    }

    override suspend fun delete(ref: String) {
        val state = readState().values.toMutableMap().apply { remove(ref) }
        writeState(SecretState(state))
    }

    private fun readState(): SecretState {
        if (!file.exists()) return SecretState()
        return runCatching {
            val bytes = file.readBytes()
            require(bytes.size > IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(TAG_LENGTH, bytes.copyOfRange(0, IV_SIZE)),
            )
            json.decodeFromString<SecretState>(cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)).decodeToString())
        }.getOrDefault(SecretState())
    }

    private fun writeState(state: SecretState) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val temporary = File(file.parentFile, "$FILE_NAME.tmp")
        temporary.writeBytes(cipher.iv + cipher.doFinal(json.encodeToString(state).encodeToByteArray()))
        temporary.copyTo(file, overwrite = true)
        temporary.delete()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    @Serializable
    private data class SecretState(val values: Map<String, String> = emptyMap())

    private companion object {
        const val FILE_NAME = "opencode_secrets.enc"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rikkahub_opencode_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_LENGTH = 128
    }
}
