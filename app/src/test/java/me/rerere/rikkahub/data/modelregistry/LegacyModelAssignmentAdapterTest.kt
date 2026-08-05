package me.rerere.rikkahub.data.modelregistry

import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class LegacyModelAssignmentAdapterTest {
    private val uuidA = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val uuidB = Uuid.parse("00000000-0000-0000-0000-000000000002")
    private val chatModelId = Uuid.parse("00000000-0000-0000-0000-000000000003")
    private val fastModelId = Uuid.parse("00000000-0000-0000-0000-000000000004")
    private val suggestionModelId = Uuid.parse("00000000-0000-0000-0000-000000000005")
    private val appScope = AppScope()
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        appScope.cancel()
        testScope.cancel()
    }

    @Test
    fun readsTitleAndTranslationIds() = runBlocking {
        val store = fakeSettingsStore(Settings(titleModelId = uuidA, translateModeId = uuidB))
        val adapter = SettingsLegacyModelAssignmentAdapter(store, testScope)

        assertEquals(uuidA.toString(), adapter.titleModelId.first())
        assertEquals(uuidB.toString(), adapter.translationModelId.first())
    }

    @Test
    fun writesTitleAndTranslationAndClearsTitle() = runBlocking {
        val provider = ProviderSetting.OpenAI(
            id = Uuid.parse("00000000-0000-0000-0000-000000000010"),
            enabled = true,
            name = "Test provider",
            models = listOf(
                Model(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000011"),
                    displayName = "Test model",
                )
            ),
            apiKey = "test-key",
            baseUrl = "https://example.com",
            chatCompletionsPath = "/chat/completions",
            useResponseApi = false,
            balanceOption = BalanceOption(enabled = false),
        )
        val store = fakeSettingsStore(
            Settings(
                chatModelId = chatModelId,
                fastModelId = fastModelId,
                suggestionModelId = suggestionModelId,
                providers = listOf(provider),
            )
        )
        val adapter = SettingsLegacyModelAssignmentAdapter(store, testScope)

        adapter.setTitleModel(uuidA.toString())
        assertEquals(uuidA.toString(), adapter.titleModelId.value)

        adapter.setTranslationModel(uuidB.toString())
        assertEquals(uuidB.toString(), adapter.translationModelId.value)

        val persisted = store.settingsFlow.first()
        assertEquals(uuidA, persisted.titleModelId)
        assertEquals(uuidB, persisted.translateModeId)
        assertEquals(chatModelId, persisted.chatModelId)
        assertEquals(fastModelId, persisted.fastModelId)
        assertEquals(suggestionModelId, persisted.suggestionModelId)
        assertEquals(provider, persisted.providers.single())

        adapter.setTitleModel(null)
        assertNull(adapter.titleModelId.value)
        assertNull(store.settingsFlow.first().titleModelId)
    }

    @Test
    fun clearingTranslationFailsWithoutChangingPersistedId() = runBlocking {
        val store = fakeSettingsStore(Settings(translateModeId = uuidB))
        val adapter = SettingsLegacyModelAssignmentAdapter(store, testScope)

        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { adapter.setTranslationModel(null) }
        }
        assertEquals(uuidB, store.settingsFlow.first().translateModeId)
    }

    @Test
    fun malformedIdsAreRejected() = runBlocking {
        val adapter = SettingsLegacyModelAssignmentAdapter(fakeSettingsStore(Settings()), testScope)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { adapter.setTitleModel("not-a-uuid") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { adapter.setTranslationModel("not-a-uuid") }
        }
        Unit
    }

    private suspend fun fakeSettingsStore(settings: Settings): SettingsStore {
        val store = SettingsStore(RuntimeEnvironment.getApplication(), appScope)
        store.update(settings)
        return store
    }
}
