package me.rerere.locallm.litert.terminal

import java.security.MessageDigest
import me.rerere.locallm.litert.CapabilityGrant
import me.rerere.locallm.litert.workspace.CommandEffectAnalyzer

/**
 * Phase F (roadmap F6). The set of side effects a process command would have, derived
 * deterministically from [CommandEffectAnalyzer]. This is the broker-visible projection of a
 * command — used to gate execution and recorded on [ProcessReceipt].
 */
enum class ProcessEffect {
    READ_LOCAL_DATA,
    WRITE_LOCAL_DATA,
    EXECUTE_CODE,
    SEND_NETWORK_REQUEST,
    MODIFY_CONFIGURATION,
}

/**
 * A local-llm-safe projection of the (app-side) `ToolExecutionPlan`. Because [CapabilityGrant]
 * and the analyzer live in local-llm but `ToolExecutionPlan` lives in the app module, this
 * carries everything the broker needs to authorise and later audit a process start without
 * depending on the app. [commandDigest] is a stable SHA-256 fingerprint over the canonical
 * command + effects, so a receipt can be correlated back to exactly what was gated.
 */
data class ProcessEffectPlan(
    val processRef: ProcessRef,
    val command: List<String>,
    val effects: Set<ProcessEffect>,
    val reads: Set<String>,
    val writes: Set<String>,
    val network: Boolean,
    val nativeExecution: Boolean,
    val commandDigest: String,
) {
    companion object {
        /**
         * Build a plan, deriving [commandDigest] from the remaining fields. [processRef] is a
 * provisional ref; the authoritative ref is issued only once the backend
         * accepts the start.
         */
        fun of(
            processRef: ProcessRef,
            command: List<String>,
            effects: Set<ProcessEffect>,
            reads: Set<String>,
            writes: Set<String>,
            network: Boolean,
            nativeExecution: Boolean,
        ): ProcessEffectPlan {
            val base = ProcessEffectPlan(processRef, command, effects, reads, writes, network, nativeExecution, "")
            return base.copy(commandDigest = base.digest())
        }
    }
}

/**
 * SHA-256 hex over the canonical string: command joined with '\u0000', sorted effect names,
 * sorted reads, sorted writes, network flag, nativeExecution flag. Deterministic — equal inputs
 * always yield an equal digest, and any of those inputs changing changes the digest.
 */
fun ProcessEffectPlan.digest(): String {
    val canonical = buildString {
        append(command.joinToString("\u0000"))
        append('\n')
        append(effects.map { it.name }.sorted().joinToString(","))
        append('\n')
        append(reads.sorted().joinToString("\u0000"))
        append('\n')
        append(writes.sorted().joinToString("\u0000"))
        append('\n')
        append(network)
        append('\n')
        append(nativeExecution)
    }
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Outcome of [ProcessGate.evaluate]. */
data class ProcessGateResult(val decision: ProcessGate.Decision)

/**
 * Phase F (roadmap F6). Capability/effect gate placed before every process start:
 *
 * `command -> CommandEffectAnalyzer -> ProcessGate -> ResourceBudget -> backend.start`
 *
 * The gate derives the command's effects, rejects anything the [CapabilityGrant] does not
 * authorise, and otherwise returns an [ProcessGate.Decision.Allowed] carrying a
 * [ProcessEffectPlan] the broker and [ProcessReceipt] can see.
 */
class ProcessGate(private val grant: CapabilityGrant) {

    sealed interface Decision {
        /** The command is authorised; [plan] is the broker-visible effect projection. */
        data class Allowed(val plan: ProcessEffectPlan) : Decision

        /** The command was rejected; [reason] is a stable machine-readable code. */
        data class Denied(val reason: String) : Decision
    }

    /** Analyse [command] against [grant] and return the decision. Never executes the command. */
    suspend fun evaluate(command: List<String>, workingDirectory: String?): ProcessGateResult {
        val analyzed = CommandEffectAnalyzer.analyze(command)

        val effects = LinkedHashSet<ProcessEffect>()
        if (analyzed.reads.isNotEmpty()) effects += ProcessEffect.READ_LOCAL_DATA
        if (analyzed.writes.isNotEmpty()) effects += ProcessEffect.WRITE_LOCAL_DATA
        if (analyzed.nativeExecution) effects += ProcessEffect.EXECUTE_CODE
        if (analyzed.network) effects += ProcessEffect.SEND_NETWORK_REQUEST
        // MODIFY_CONFIGURATION has no explicit signal from the analyzer today; it stays unset
        // unless a future analyzer surfaces a config-write signal.

        if (analyzed.nativeExecution && !grant.isAllowed("process_execute")) {
            return ProcessGateResult(Decision.Denied("native_execution_denied"))
        }
        if (analyzed.network && !grant.isAllowed("process_network")) {
            return ProcessGateResult(Decision.Denied("network_denied"))
        }
        for (path in analyzed.writes.sorted()) {
            if (!grant.scopes.isAllowingFile(path, "write")) {
                return ProcessGateResult(Decision.Denied("write_not_allowed: $path"))
            }
        }
        for (path in analyzed.reads.sorted()) {
            if (!grant.scopes.isAllowingFile(path, "read")) {
                return ProcessGateResult(Decision.Denied("read_not_allowed: $path"))
            }
        }

        val plan = ProcessEffectPlan.of(
            processRef = ProcessRef("planned:${command.firstOrNull() ?: "empty"}"),
            command = command,
            effects = effects.toSet(),
            reads = analyzed.reads,
            writes = analyzed.writes,
            network = analyzed.network,
            nativeExecution = analyzed.nativeExecution,
        )
        return ProcessGateResult(Decision.Allowed(plan))
    }
}
