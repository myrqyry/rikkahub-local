package me.rerere.rikkahub.data.modelregistry

import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
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
    private val appScope = AppScope()

    @After
    fun tearDown() {
        appScope.cancel()
    }

    @Test
    fun readsTitleAndTranslationIds() = runBlocking {
        val store = fakeSettingsStore(Settings(titleModelId = uuidA, translateModeId = uuidB))
        val adapter = SettingsLegacyModelAssignmentAdapter(store, appScope)

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
        val store = fakeSettingsStore(Settings(providers = listOf(provider)))
        val adapter = SettingsLegacyModelAssignmentAdapter(store, appScope)

        adapter.setTitleModel(uuidA.toString())
        adapter.setTranslationModel(uuidB.toString())
        assertEquals(uuidA, store.settingsFlow.first().titleModelId)
        assertEquals(uuidB, store.settingsFlow.first().translateModeId)
        assertEquals(provider, store.settingsFlow.first().providers.single())

        adapter.setTitleModel(null)
        assertNull(store.settingsFlow.first().titleModelId)
    }

    @Test
    fun clearingTranslationFailsWithoutChangingPersistedId() = runBlocking {
        val store = fakeSettingsStore(Settings(translateModeId = uuidB))
        val adapter = SettingsLegacyModelAssignmentAdapter(store, appScope)

        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { adapter.setTranslationModel(null) }
        }
        assertEquals(uuidB, store.settingsFlow.first().translateModeId)
    }

    @Test
    fun malformedIdsAreRejected() = runBlocking {
        val adapter = SettingsLegacyModelAssignmentAdapter(fakeSettingsStore(Settings()), appScope)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { adapter.setTitleModel("not-a-uuid") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { adapter.setTranslationModel("not-a-uuid") }
        }
        Unit
    }

    private suspend fun fakeSettingsStore(settings: Settings): SettingsStore {
        val store = SettingsStore(RuntimeEnvironment.getApplication(), AppScope())
        store.update(settings)
        return store
    }
}
