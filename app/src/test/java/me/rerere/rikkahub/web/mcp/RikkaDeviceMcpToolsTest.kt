package me.rerere.rikkahub.web.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RikkaDeviceMcpToolsTest {
    @Test
    fun `device export requires startup auth current auth and explicit opt in`() {
        assertEquals(true, deviceToolExportEnabled(true, true, true))
        assertEquals(false, deviceToolExportEnabled(false, true, true))
        assertEquals(false, deviceToolExportEnabled(true, false, true))
        assertEquals(false, deviceToolExportEnabled(true, true, false))
    }

    @Test
    fun `only explicit safe device tools are exported`() {
        val exported = exportRemoteDeviceTools(
            listOf(
                fakeTool("get_battery_status"),
                fakeTool("get_wifi_info"),
                fakeTool("list_installed_apps"),
                fakeTool("launch_app"),
            ),
        )

        assertEquals(listOf("rikka.device.get_battery_status"), exported.map { it.name })
    }

    @Test
    fun `exported tools preserve schema and execution`() = runBlocking {
        val exported = exportRemoteDeviceTools(
            listOf(fakeTool("get_volume", output = """{"percent":42}""")),
        )

        val tool = exported.single()
        assertEquals("rikka.device.get_volume", tool.name)
        assertNotNull(tool.inputSchema)
        assertEquals(
            """{"percent":42}""",
            (tool.call(buildJsonObject {}).single() as UIMessagePart.Text).text,
        )
    }

    @Test
    fun `duplicate safe tools collapse and output is deterministic`() {
        val exported = exportRemoteDeviceTools(
            listOf(
                fakeTool("get_volume"),
                fakeTool("get_battery_status"),
                fakeTool("get_volume"),
            ),
        )

        assertEquals(
            listOf(
                "rikka.device.get_battery_status",
                "rikka.device.get_volume",
            ),
            exported.map { it.name },
        )
    }

    private fun fakeTool(name: String, output: String = name) = Tool(
        name = name,
        description = "test $name",
        parameters = { InputSchema.Obj(buildJsonObject {}) },
        execute = { listOf(UIMessagePart.Text(output)) },
    )
}
