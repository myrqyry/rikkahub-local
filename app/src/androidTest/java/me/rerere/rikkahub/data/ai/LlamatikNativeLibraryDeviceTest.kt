package me.rerere.rikkahub.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Read-only native-load smoke for the shipping Llamatik Android artifact.
 * No model is loaded, no settings are changed, and no inference is claimed.
 * Run manually via adb shell am instrument; never via connectedDebugAndroidTest
 * on an installed user-data package.
 */
@RunWith(AndroidJUnit4::class)
class LlamatikNativeLibraryDeviceTest {
    @Test
    fun androidArm64JniLibraryLoads() {
        // The app consumes Llamatik through :local-llm's implementation dependency.
        // App androidTest does not expose that class on its compile classpath, so
        // resolve the actual packaged runtime type without introducing another copy
        // of the Llamatik dependency. Class.forName with initialize=true executes
        // the initializer containing System.loadLibrary("llama_jni").
        val name = "com.llamatik.library.platform.LlamaBridge"
        val type = Class.forName(name, true, javaClass.classLoader)
        assertEquals(name, type.name)
    }
}
