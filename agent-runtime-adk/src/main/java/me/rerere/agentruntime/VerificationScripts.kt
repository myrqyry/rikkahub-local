package me.rerere.agentruntime

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration

/**
 * A single named project verification script, mirroring Coday's `Script` type
 * (`{ description, command, parametersDescription?, requireConfirmation? }`).
 *
 * The command runs from the project root. A `PARAMETERS` token inside the
 * command is replaced with the tool-call's `stringParameters`; otherwise the
 * parameters are appended as a suffix.
 */
data class VerificationCommand(
    val command: String,
    val description: String,
)

/**
 * Project verification scripts exposed to the agent as tools — deterministic
 * build/test/lint rituals so an agent never has to rediscover them.
 *
 * Mirrors Coday's `Scripts` type: `{ [key: string]: Script }` where the key is
 * the script name the agent invokes.
 */
data class VerificationScripts(
    val commands: Map<String, VerificationCommand> = emptyMap(),
) {
    /** Renders "name — description" lines for the agent. */
    fun describe(): String =
        commands.entries.joinToString("\n") { (name, cmd) -> "$name — ${cmd.description}" }

    /** Looks up a single script by name, or null when unknown. */
    fun script(name: String): VerificationCommand? = commands[name]
}

/**
 * Pure, JVM-testable core of [RunVerificationTool.execute]. Free of the ADK
 * ToolContext so it can be unit-tested without constructing one.
 */
fun runVerificationToolRun(
    scripts: VerificationScripts,
    args: Map<String, Any?>,
    runner: (command: String) -> String,
): Any {
    val name = args["script"] as? String ?: return mapOf("error" to "Missing 'script' argument")
    val script = scripts.script(name) ?: return mapOf("error" to "No verification script named '$name'")
    val parameters = (args["stringParameters"] as? String).orEmpty()
    val command = if (script.command.contains("PARAMETERS")) {
        script.command.replace("PARAMETERS", parameters)
    } else if (parameters.isNotEmpty()) {
        "${script.command} $parameters"
    } else {
        script.command
    }
    return try {
        mapOf("result" to runner(command))
    } catch (e: Exception) {
        mapOf("error" to (e.message ?: "Verification script '$name' failed"))
    }
}

/**
 * ADK [FunctionTool] exposing the project's verification scripts. Nothing runs
 * unless the host injects a [runner]; unit tests and the app provide it.
 */
class RunVerificationTool(
    private val scripts: VerificationScripts,
    private val runner: (command: String) -> String,
) : FunctionTool(
    name = "runVerification",
    description = "Runs a project verification script (build/test/lint) and returns its output. " +
        "Args: script (one of: ${scripts.commands.keys.sorted().joinToString(", ")}), stringParameters (optional).",
) {
    override fun declaration(): FunctionDeclaration = FunctionDeclaration(name = name, description = description)

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        runVerificationToolRun(scripts = scripts, args = args, runner = runner)
}
