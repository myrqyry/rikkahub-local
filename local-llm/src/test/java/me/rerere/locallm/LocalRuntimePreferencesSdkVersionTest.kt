package me.rerere.locallm

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRuntimePreferencesSdkVersionTest {

    @Test
    fun sdkUpgradeInvalidatesMissingOrOlderVersionButNotCurrentVersion() {
        assertTrue(LocalRuntimePreferences.shouldInvalidateSdkVersion(null))
        assertTrue(LocalRuntimePreferences.shouldInvalidateSdkVersion("0.17.0"))
        assertFalse(
            LocalRuntimePreferences.shouldInvalidateSdkVersion(
                LocalRuntimePreferences.LITERTLM_SDK_VERSION,
            ),
        )
    }

    @Test
    fun sdkVersionMarkerMatchesBothPinnedGradleDependencies() {
        // Gradle JVM tests run from this module's project directory.
        val moduleDir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        val localGradle = File(moduleDir, "build.gradle.kts")
        val appGradle = File(moduleDir, "../app/build.gradle.kts")
        assertTrue("Expected local-llm/build.gradle.kts at $localGradle", localGradle.isFile)
        assertTrue("Expected app/build.gradle.kts at $appGradle", appGradle.isFile)

        val dependencyPattern =
            Regex("""implementation\("com\.google\.ai\.edge\.litertlm:litertlm-android:([^"]+)"\)""")

        fun pinnedVersion(file: File): String {
            val matches = dependencyPattern.findAll(file.readText()).toList()
            assertEquals("Expected exactly one LiteRT-LM dependency in $file", 1, matches.size)
            return matches.single().groupValues[1]
        }

        val sdkVersion = LocalRuntimePreferences.LITERTLM_SDK_VERSION
        assertEquals("Local SDK marker must match local-llm dependency", pinnedVersion(localGradle), sdkVersion)
        assertEquals("App and local-llm must package the same SDK version", pinnedVersion(appGradle), sdkVersion)
    }
}
