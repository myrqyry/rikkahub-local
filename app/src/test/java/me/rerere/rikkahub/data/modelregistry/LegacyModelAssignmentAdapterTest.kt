package me.rerere.rikkahub.data.modelregistry

import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertThrows
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class LegacyModelAssignmentAdapterTest {
    private val uuidA = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val uuidB = Uuid.parse("00000000-0000-0000-0000-000000000002")

    @Test
    fun readsTitleAndTranslationIds() = runBlocking {
        val store = fakeSettingsStore(Settings(titleModelId = uuidA, translateModeId = uuidB))
        val adapter = SettingsLegacyModelAssignmentAdapter(store)

        assertEquals(uuidA.toString(), adapter.titleModelId.first())
        assertEquals(uuidB.toString(), adapter.translationModelId.first())
    }

    @Test
    fun writesTitleAndTranslationAndClearsTitle() = runBlocking {
        val store = fakeSettingsStore(Settings())
        val adapter = SettingsLegacyModelAssignmentAdapter(store)

        adapter.setTitleModel(uuidA.toString())
        adapter.setTranslationModel(uuidB.toString())
        assertEquals(uuidA, store.settingsFlow.first().titleModelId)
        assertEquals(uuidB, store.settingsFlow.first().translateModeId)

        adapter.setTitleModel(null)
        assertNull(store.settingsFlow.first().titleModelId)
    }

    @Test
    fun clearingTranslationFailsWithoutChangingPersistedId() = runBlocking {
        val store = fakeSettingsStore(Settings(translateModeId = uuidB))
        val adapter = SettingsLegacyModelAssignmentAdapter(store)

        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { adapter.setTranslationModel(null) }
        }
        assertEquals(uuidB, store.settingsFlow.first().translateModeId)
    }

    @Test
    fun malformedIdsAreRejected() = runBlocking {
        val adapter = SettingsLegacyModelAssignmentAdapter(fakeSettingsStore(Settings()))

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
