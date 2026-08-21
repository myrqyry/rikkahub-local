package me.rerere.agentruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationScriptsTest {

    private fun scripts() = VerificationScripts(
        commands = mapOf(
            "build" to VerificationCommand(command = "./gradlew assembleDebug", description = "Build the debug APK"),
            "test" to VerificationCommand(command = "./gradlew test", description = "Run the unit test suite"),
            "lint" to VerificationCommand(command = "./gradlew lint", description = "Run Android lint"),
        ),
    )

    @Test
    fun `describe lists every script as name - description`() {
        val text = scripts().describe()
        assertTrue(text.contains("build"))
        assertTrue(text.contains("Build the debug APK"))
        assertTrue(text.contains("test"))
        assertTrue(text.contains("Run the unit test suite"))
        assertTrue(text.contains("lint"))
        assertTrue(text.contains("Run Android lint"))
    }

    @Test
    fun `script returns the matching command`() {
        assertEquals("Build the debug APK", scripts().script("build")?.description)
        assertEquals("./gradlew test", scripts().script("test")?.command)
    }

    @Test
    fun `script returns null for unknown name`() {
        assertEquals(null, scripts().script("deploy"))
    }

    @Test
    fun `runVerificationToolRun executes the named script and returns its output`() {
        val ran = mutableListOf<String>()
        val result = runVerificationToolRun(
            scripts = scripts(),
            args = mapOf("script" to "test"),
            runner = { command ->
                ran.add(command)
                "tests passed"
            },
        )
        assertEquals(listOf("./gradlew test"), ran)
        assertEquals("tests passed", (result as Map<*, *>)["result"])
    }

    @Test
    fun `runVerificationToolRun returns error for missing script argument`() {
        val result = runVerificationToolRun(
            scripts = scripts(),
            args = mapOf<String, Any?>(),
            runner = { "unused" },
        )
        assertTrue((result as Map<*, *>)["error"].toString().contains("script"))
    }

    @Test
    fun `runVerificationToolRun returns error for unknown script`() {
        val result = runVerificationToolRun(
            scripts = scripts(),
            args = mapOf("script" to "deploy"),
            runner = { "unused" },
        )
        assertTrue((result as Map<*, *>)["error"].toString().contains("deploy"))
    }

    @Test
    fun `runVerificationToolRun surfaces runner failures as errors`() {
        val result = runVerificationToolRun(
            scripts = scripts(),
            args = mapOf("script" to "lint"),
            runner = { error("lint crashed") },
        )
        assertTrue((result as Map<*, *>)["error"].toString().contains("lint crashed"))
    }

    @Test
    fun `runVerificationToolRun appends optional stringParameters to the command`() {
        val ran = mutableListOf<String>()
        val result = runVerificationToolRun(
            scripts = scripts(),
            args = mapOf("script" to "test", "stringParameters" to "ToolFilterTest"),
            runner = { command ->
                ran.add(command)
                "ok"
            },
        )
        assertEquals(listOf("./gradlew test ToolFilterTest"), ran)
        assertEquals("ok", (result as Map<*, *>)["result"])
    }
}
