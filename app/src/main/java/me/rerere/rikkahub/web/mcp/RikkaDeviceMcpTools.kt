package me.rerere.rikkahub.web.mcp

import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.tools.ToolInvocationContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant

private const val DEVICE_TOOL_PREFIX = "rikka.device."

/**
 * Exposes a deliberately small read-only subset of RikkaHub's native Android tools over MCP.
 *
 * The web server is not a second authority system: remote export reuses the current assistant's
 * local-tool selections, then narrows that set again through [REMOTE_SAFE_TOOL_NAMES]. More
 * sensitive reads and every action tool stay private to the phone until a dedicated remote-grant
 * and approval flow exists.
 */
class RikkaDeviceMcpTools(
    private val localTools: LocalTools,
    private val settingsStore: SettingsStore,
    private val authenticatedRoute: Boolean,
) {
    fun tools(): List<NativeMcpTool> {
        val settings = settingsStore.settingsFlow.value
        val exportEnabled = deviceToolExportEnabled(
            authenticatedRoute = authenticatedRoute,
            jwtEnabled = settings.webServerJwtEnabled,
            exportEnabled = settings.webServerMcpDeviceToolsEnabled,
        )
        if (!exportEnabled) return emptyList()

        val assistant = settings.getCurrentAssistant()
        return exportRemoteDeviceTools(
            localTools.getTools(
                options = assistant.localTools,
                invocationContext = ToolInvocationContext.EMPTY,
            ),
        )
    }

    companion object {
        /**
         * Read-only, low-risk device state only. In particular this excludes Wi-Fi identifiers,
         * audio-device names, media metadata, installed-app inventory, sensors, notifications,
         * contacts, messages, location, files,
         * accessibility/UI control, app launching, camera/mic capture, and shell execution.
         */
        internal val REMOTE_SAFE_TOOL_NAMES = setOf(
            "get_time_info",
            "get_battery_status",
            "get_storage_info",
            "get_brightness",
            "get_volume",
        )
    }
}

internal fun deviceToolExportEnabled(
    authenticatedRoute: Boolean,
    jwtEnabled: Boolean,
    exportEnabled: Boolean,
): Boolean = authenticatedRoute && jwtEnabled && exportEnabled

internal fun exportRemoteDeviceTools(tools: List<Tool>): List<NativeMcpTool> = tools
    .asSequence()
    .filter { it.name in RikkaDeviceMcpTools.REMOTE_SAFE_TOOL_NAMES }
    .distinctBy { it.name }
    .map { tool ->
        NativeMcpTool(
            name = DEVICE_TOOL_PREFIX + tool.name,
            description = tool.description,
            inputSchema = tool.parameters(),
            call = { arguments -> tool.execute(arguments) },
        )
    }
    .sortedBy { it.name }
    .toList()
