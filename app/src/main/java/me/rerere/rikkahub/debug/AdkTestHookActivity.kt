package me.rerere.rikkahub.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.rerere.agentruntime.AgentEvent
import me.rerere.agentruntime.AssistantDefinition
import me.rerere.agentruntime.SimpleAgentRuntime
import me.rerere.agentruntime.adk.ChatProviderAdkModel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.LocalRuntimePreferences
import me.rerere.locallm.llamacpp.LlamaCppProvider
import me.rerere.rikkahub.BuildConfig
import java.io.File

/**
 * Debug-only verification hook for the ADK Kotlin PoC, driven from adb:
 *
 *   adb shell am start -n excp.rikkahub.local.debug/.debug.AdkTestHookActivity \
 *     --es prompt "hello" --es modelPath <path>
 *
 * Routes one Rikkahub assistant (a llama.cpp chat model wrapped as an ADK Model via
 * [ChatProviderAdkModel]) plus one FunctionTool through ADK's LlmAgent + InMemoryRunner
 * (see [SimpleAgentRuntime]), and logs the resulting [AgentEvent] stream. Mirrors the
 * LlamaCppTestHookActivity pattern (Activity not BroadcastReceiver — the Android 17
 * preview on the test device stalls the broadcast queue, see SESSION-STATE.md).
 */
class AdkTestHookActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "AdkTestHook is debug-only; ignoring launch in non-debug builds")
            finish()
            return
        }
        val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: "What is 2 + 2? Use the time tool to check the current hour."
        val modelPath = intent.getStringExtra(EXTRA_MODEL_PATH)
        Log.i(TAG, "hook start prompt=\"$prompt\" modelPath=${modelPath ?: "auto-locate"}")
        scope.launch {
            try {
                runHook(prompt, modelPath)
            } catch (t: Throwable) {
                Log.e(TAG, "hook failed", t)
            } finally {
                finish()
            }
        }
    }

    private suspend fun runHook(prompt: String, modelPath: String?) {
        val prefs = LocalRuntimePreferences(applicationContext)
        val provider = LlamaCppProvider(context = applicationContext, prefs = prefs)

        val modelFile = if (!modelPath.isNullOrBlank()) {
            File(modelPath).takeIf { it.isFile }
        } else {
            val modelDir = File(applicationContext.filesDir, "local-models/llama-cpp")
            modelDir.listFiles()
                ?.filter { it.extension.equals("gguf", ignoreCase = true) }
                ?.maxByOrNull { it.length() }
        }
        if (modelFile == null) {
            error("no GGUF model found — pass --es modelPath <path> to a registered llama-cpp model")
        }
        prefs.addInstalledModel(LocalRuntime.LlamaCpp, modelFile.name, modelFile.absolutePath)

        val model = Model(modelId = modelFile.name, displayName = modelFile.name)
        val providerSetting = ProviderSetting.LlamaCppLocal(enabled = true, models = listOf(model))
        val adkModel = ChatProviderAdkModel(
            name = "local/${modelFile.name}",
            provider = provider,
            providerSetting = providerSetting,
            model = model,
        )

        val tool = object : FunctionTool(
            name = "current_hour",
            description = "Returns the current hour of the day (0-23) on this device.",
        ) {
            override fun declaration(): FunctionDeclaration = FunctionDeclaration(
                name = "current_hour",
                description = "Returns the current hour of the day (0-23) on this device.",
            )

            override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
                mapOf("hour" to java.time.LocalDateTime.now().hour)
        }

        val assistant = AssistantDefinition(
            name = "adk-demo",
            model = adkModel,
            systemPrompt = "You are a concise assistant. When the user asks about time, use the current_hour tool.",
            tools = listOf(tool),
        )

        val runtime = SimpleAgentRuntime()
        val runStart = System.currentTimeMillis()
        var firstDeltaMs: Long = -1
        var textChunks = 0
        val collected = StringBuilder()
        var turnCompleteSeen = false
        var endOfAgentSeen = false

        runtime.run(assistant, prompt).collect { event ->
            when (event) {
                is AgentEvent.Text -> {
                    if (event.partial) {
                        textChunks++
                        if (firstDeltaMs < 0) firstDeltaMs = System.currentTimeMillis() - runStart
                    }
                    collected.append(event.text)
                    Log.i(TAG, "event text partial=${event.partial} \"${event.text}\"")
                }
                is AgentEvent.ToolCall ->
                    Log.i(TAG, "event tool_call name=${event.name} args=${event.args}")
                is AgentEvent.ToolResult ->
                    Log.i(TAG, "event tool_result name=${event.name} result=${event.result}")
                is AgentEvent.Error ->
                    Log.e(TAG, "event error ${event.message}")
                AgentEvent.TurnComplete -> {
                    turnCompleteSeen = true
                    Log.i(TAG, "event turn_complete")
                }
                AgentEvent.EndOfAgent -> {
                    endOfAgentSeen = true
                    Log.i(TAG, "event end_of_agent")
                }
            }
        }

        val elapsedMs = System.currentTimeMillis() - runStart
        Log.i(
            TAG,
            "hook done model=${modelFile.name} textChunks=$textChunks chars=${collected.length} " +
                "firstDeltaMs=$firstDeltaMs elapsedMs=$elapsedMs turnComplete=$turnCompleteSeen endOfAgent=$endOfAgentSeen",
        )
        Log.i(TAG, "hook reply: ${collected.toString().take(400)}")
        Log.i(TAG, "hook complete")
    }

    companion object {
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_MODEL_PATH = "modelPath"

        private const val TAG = "AdkTestHook"
    }
}
