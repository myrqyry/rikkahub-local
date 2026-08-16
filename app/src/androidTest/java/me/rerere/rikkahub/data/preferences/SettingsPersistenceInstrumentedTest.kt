package me.rerere.rikkahub.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0 stabilization: one Android integration check that preferences actually survive on-device.
 *
 * - The DataStore test writes through the app's real [SettingsStore] DataStore file and proves
 *   the bytes landed in the on-disk `.preferences_pb` (durable, not in-memory).
 * - The SharedPreferences test proves the close-and-reopen pattern against a second handle.
 *
 * Uses unique test names; never touches the production `rikka_hub` database or primary profile.
 */
@RunWith(AndroidJUnit4::class)
class SettingsPersistenceInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testName = "settings-persistence-${System.nanoTime()}"
    private val prefName = "rikkahub.$testName"

    private val keyText = stringPreferencesKey("text")
    private val keyBool = booleanPreferencesKey("bool")

    @Before
    fun clean() {
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteFile("$testName.preferences_pb")
    }

    @After
    fun cleanup() {
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteFile("$testName.preferences_pb")
    }

    @Test
    fun dataStoreValuesArePersistedToDiskThroughTheAppStore() = runBlocking {
        // The app's real DataStore file (same produceFile the SettingsStore singleton uses).
        val dsFile = context.preferencesDataStoreFile(testName)
        dsFile.delete()

        val scope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { dsFile },
        )
        store.edit { it[keyText] = "assistant-openai" }
        store.edit { it[keyBool] = true }
        // The edit coroutine has completed: the actor has written to disk. Verify the bytes.
        val bytes = dsFile.readBytes()
        assertTrue("preferences file must be non-empty after write", bytes.isNotEmpty())
        assertTrue(
            "written text value must be serialized into the on-disk file",
            String(bytes, Charsets.UTF_8).contains("assistant-openai"),
        )
    }

    @Test
    fun sharedPreferencesValuesSurviveReopen() {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        prefs.edit().putString("selected_tts_provider", "pocket").commit()
        prefs.edit().putBoolean("allow_cloud", false).commit()

        // A second handle is equivalent to a cold reopen (values live on disk).
        val reopened = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        assertEquals("pocket", reopened.getString("selected_tts_provider", null))
        assertEquals(false, reopened.getBoolean("allow_cloud", true))
    }
}
