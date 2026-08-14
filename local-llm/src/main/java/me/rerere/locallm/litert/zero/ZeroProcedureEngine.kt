package me.rerere.locallm.litert.zero

import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * Phase 7 — Zero deterministic execution substrate (prototype).
 *
 * Two stages, both deterministic:
 *
 * 1. [compile] validates the [ZeroProcedure] graph without executing anything:
 *    - duplicate step ids
 *    - unknown tools (not present in [toolCatalog])
 *    - dangling `{{stepId...}}` references (target step does not exist, or references itself)
 *    - reference cycles (a step transitively depending on itself)
 *    Returns a topological [ZeroCompilationResult.Valid] execution order, or an
 *    [ZeroCompilationResult.Invalid] list of [ZeroDiagnostic]s. No tool is invoked here.
 *
 * 2. [execute] runs the compiled order sequentially (still deterministic given identical
 *    tool outputs): each step's args are template-resolved against the outputs of steps it
 *    references, then the tool is invoked under a per-step timeout. `failFast` semantics
 *    follow [ZeroProcedure.failFast].
 *
 * The substrate never performs capability gating or approval — that stays the concern of
 * the [me.rerere.locallm.litert.ActionPlanExecutor] gate that selected this executor. This
 * class is intentionally Android-light (only [Log]) so it can run in JVM unit tests.
 */
class ZeroProcedureEngine(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /** Regex matching `{{stepId}}` or `{{stepId.a.b}}` inside a JSON string value. */
    private val refPattern = Regex("""\{\{\s*([A-Za-z0-9_\-]+(?:\.[A-Za-z0-9_\-]+)*)\s*}}""")

    /**
     * Validate the procedure and compute a deterministic topological execution order.
     * Pure — never calls a tool. Null-returns toolCatalog lookup failures only.
     */
    fun compile(
        procedure: ZeroProcedure,
        toolCatalog: Map<String, Tool>,
    ): ZeroCompilationResult {
        val diagnostics = mutableListOf<ZeroDiagnostic>()

        // 1. Duplicate step ids
        val ids = procedure.steps.map { it.stepId }
        ids.groupBy { it }.filterValues { it.size > 1 }.keys.forEach { dup ->
            diagnostics += ZeroDiagnostic("ZERO_DUP_STEP", dup, "duplicate step id '$dup'")
        }
        if (diagnostics.any { it.code == "ZERO_DUP_STEP" }) {
            return ZeroCompilationResult.Invalid(diagnostics)
        }

        val byId = procedure.steps.associateBy { it.stepId }

        // 2. Per-step structural checks: known tool, self ref, dangling refs
        for (step in procedure.steps) {
            if (step.tool !in toolCatalog) {
                diagnostics += ZeroDiagnostic(
                    "ZERO_UNKNOWN_TOOL", step.stepId,
                    "step '${step.stepId}' references unknown tool '${step.tool}'",
                )
            }
            val refs = collectRefs(step.args)
            for (ref in refs) {
                val targetStepId = ref.substringBefore('.')
                when {
                    targetStepId == step.stepId -> diagnostics += ZeroDiagnostic(
                        "ZERO_SELF_REF", step.stepId,
                        "step '${step.stepId}' references its own output via '{{$ref}}'",
                    )
                    targetStepId !in byId -> diagnostics += ZeroDiagnostic(
                        "ZERO_DANGLING_REF", step.stepId,
                        "step '${step.stepId}' references unknown step '$targetStepId' via '{{$ref}}'",
                    )
                }
            }
        }

        // 3. Cycle detection on the forward reference graph
        val depOf = procedure.steps.associate { step ->
            step.stepId to collectRefs(step.args)
                .map { it.substringBefore('.') }
                .filter { it in byId && it != step.stepId }
                .toSet()
        }
        for (start in procedure.steps.map { it.stepId }) {
            val visiting = HashSet<String>()
            val visited = HashSet<String>()
            fun visit(node: String): String? {
                if (node in visiting) return node
                if (node in visited) return null
                visiting += node
                for (next in depOf[node].orEmpty()) {
                    visit(next)?.let { return it }
                }
                visiting -= node
                visited += node
                return null
            }
            visit(start)?.let { cycleRoot ->
                diagnostics += ZeroDiagnostic(
                    "ZERO_CYCLE", cycleRoot,
                    "reference cycle detected involving step '$cycleRoot'",
                )
                break
            }
        }

        if (diagnostics.isNotEmpty()) return ZeroCompilationResult.Invalid(diagnostics)

        // 4. Topological order (Kahn's algorithm) — deterministic tie-break by authoring order.
        val indegree = HashMap<String, Int>()
        byId.keys.forEach { indegree[it] = 0 }
        for (step in procedure.steps) {
            // indegree[step] = number of steps this step depends on (prerequisites).
            indegree[step.stepId] = depOf[step.stepId].orEmpty().size
        }
        val ready = ArrayDeque<String>()
        indegree.filterValues { it == 0 }.keys.sorted().forEach { ready.addLast(it) }
        val order = ArrayList<String>(procedure.steps.size)
        while (ready.isNotEmpty()) {
            val node = ready.removeFirst()
            order += node
            for (step in procedure.steps) {
                if (depOf[step.stepId].orEmpty().contains(node)) {
                    indegree[step.stepId] = indegree.getValue(step.stepId) - 1
                    if (indegree[step.stepId] == 0) ready.addLast(step.stepId)
                }
            }
        }
        // Unreachable-from-any-reference steps still appear (indegree 0), so order should
        // cover everything unless a cycle slipped through — defensive guard.
        if (order.size != procedure.steps.size) {
            val leftover = byId.keys - order.toSet()
            diagnostics += ZeroDiagnostic("ZERO_ORDER", leftover.firstOrNull(),
                "graph ordering failed; leftover steps: $leftover")
            return ZeroCompilationResult.Invalid(diagnostics)
        }
        return ZeroCompilationResult.Valid(order)
    }

    /**
     * Execute the procedure in compiled topological order. Deterministic given identical
     * tool outputs. Does not catch cancellation (structured concurrency).
     */
    suspend fun execute(
        procedure: ZeroProcedure,
        toolCatalog: Map<String, Tool>,
    ): ZeroExecutionResult {
        val compiled = compile(procedure, toolCatalog)
        if (compiled is ZeroCompilationResult.Invalid) {
            val msg = compiled.diagnostics.joinToString("; ") {
                "${it.code}(${it.stepId ?: "?"}): ${it.message}"
            }
            Log.w(TAG, "zero procedure '${procedure.id}' compile failed: $msg")
            return ZeroExecutionResult(false, emptyList(), emptyMap(), "compile_invalid: $msg")
        }
        val order = (compiled as ZeroCompilationResult.Valid).order
        val byId = procedure.steps.associateBy { it.stepId }
        val outputs = HashMap<String, JsonElement>()
        val stepResults = ArrayList<ZeroStepResult>(order.size)
        var firstError: String? = null

        for (stepId in order) {
            val step = byId.getValue(stepId)
            if (firstError != null && procedure.failFast) {
                // Skipped step after an earlier failure under failFast.
                stepResults += ZeroStepResult(stepId, success = false, error = "skipped: prior step failed")
                continue
            }
            val resolved = resolveTemplates(step.args, outputs)
            val tool = toolCatalog[step.tool]
            if (tool == null) {
                val err = "step '$stepId': unknown tool '${step.tool}'"
                stepResults += ZeroStepResult(stepId, false, error = err)
                if (firstError == null) firstError = err
                continue
            }
            val out = try {
                withTimeoutOrNull(step.timeoutSeconds * 1000L) { tool.execute(resolved) }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                val err = "step '$stepId': ${t::class.simpleName}: ${t.message.orEmpty()}".take(500)
                Log.w(TAG, err)
                stepResults += ZeroStepResult(stepId, false, error = err)
                if (firstError == null) firstError = err
                continue
            }
            if (out == null) {
                val err = "step '$stepId': '${step.tool}' exceeded ${step.timeoutSeconds}s"
                stepResults += ZeroStepResult(stepId, false, error = err)
                if (firstError == null) firstError = err
                continue
            }
            val output = extractOutput(out)
            outputs[stepId] = output
            stepResults += ZeroStepResult(stepId, true, output = output)
        }

        return ZeroExecutionResult(
            success = firstError == null,
            stepResults = stepResults,
            outputs = outputs,
            error = firstError,
        )
    }

    /**
     * Execute like [execute] but also emit a first-class [ZeroProcedureReceipt] with per-step
     * evidence (timing, input digest, resolved output ref, diagnostic). Deterministic; does not
     * catch cancellation. Roadmap B6.
     */
    suspend fun executeWithReceipts(
        procedure: ZeroProcedure,
        toolCatalog: Map<String, Tool>,
        procedureRevision: Long = 0L,
        nowMs: () -> Long = System::currentTimeMillis,
    ): Pair<ZeroExecutionResult, ZeroProcedureReceipt> {
        val startedAtMs = nowMs()
        val compiled = compile(procedure, toolCatalog)
        if (compiled is ZeroCompilationResult.Invalid) {
            val msg = compiled.diagnostics.joinToString("; ") {
                "${it.code}(${it.stepId ?: "?"}): ${it.message}"
            }
            Log.w(TAG, "zero procedure '${procedure.id}' compile failed: $msg")
            val diag = ZeroDiagnostic("ZERO_COMPILE_INVALID", null, msg)
            val stepReceipt = procedure.steps.map {
                ZeroStepReceipt(
                    procedureId = procedure.id,
                    procedureRevision = procedureRevision,
                    stepId = it.stepId,
                    toolName = it.tool,
                    status = StepStatus.SKIPPED,
                    startedAtMs = startedAtMs,
                    completedAtMs = startedAtMs,
                    inputDigest = digest(it.args.toString()),
                    diagnostic = ZeroDiagnostic("ZERO_COMPILE_INVALID", it.stepId, msg),
                )
            }
            val receipt = ZeroProcedureReceipt(
                procedureId = procedure.id,
                revision = procedureRevision,
                status = ProcedureStatus.FAILED,
                steps = stepReceipt,
                startedAtMs = startedAtMs,
                completedAtMs = nowMs(),
                error = "compile_invalid: $msg",
            )
            return ZeroExecutionResult(false, emptyList(), emptyMap(), "compile_invalid: $msg") to receipt
        }
        val order = (compiled as ZeroCompilationResult.Valid).order
        val byId = procedure.steps.associateBy { it.stepId }
        val outputs = HashMap<String, JsonElement>()
        val stepResults = ArrayList<ZeroStepResult>(order.size)
        val stepReceipts = ArrayList<ZeroStepReceipt>(order.size)
        var firstError: String? = null

        for (stepId in order) {
            val step = byId.getValue(stepId)
            val stepStartMs = nowMs()
            if (firstError != null && procedure.failFast) {
                stepResults += ZeroStepResult(stepId, false, error = "skipped: prior step failed")
                stepReceipts += ZeroStepReceipt(
                    procedureId = procedure.id, procedureRevision = procedureRevision,
                    stepId = stepId, toolName = step.tool, status = StepStatus.SKIPPED,
                    startedAtMs = stepStartMs, completedAtMs = nowMs(),
                    inputDigest = digest(resolveTemplates(step.args, outputs).toString()),
                    diagnostic = ZeroDiagnostic("ZERO_SKIPPED", stepId, "skipped: prior step failed"),
                )
                continue
            }
            val resolved = resolveTemplates(step.args, outputs)
            val inputDigest = digest(resolved.toString())
            val tool = toolCatalog[step.tool]
            if (tool == null) {
                val err = "step '$stepId': unknown tool '${step.tool}'"
                stepResults += ZeroStepResult(stepId, false, error = err)
                stepReceipts += ZeroStepReceipt(
                    procedureId = procedure.id, procedureRevision = procedureRevision,
                    stepId = stepId, toolName = step.tool, status = StepStatus.FAILED,
                    startedAtMs = stepStartMs, completedAtMs = nowMs(), inputDigest = inputDigest,
                    diagnostic = ZeroDiagnostic("ZERO_UNKNOWN_TOOL", stepId, err),
                )
                if (firstError == null) firstError = err
                continue
            }
            val out = try {
                withTimeoutOrNull(step.timeoutSeconds * 1000L) { tool.execute(resolved) }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                val err = "step '$stepId': ${t::class.simpleName}: ${t.message.orEmpty()}".take(500)
                Log.w(TAG, err)
                stepResults += ZeroStepResult(stepId, false, error = err)
                stepReceipts += ZeroStepReceipt(
                    procedureId = procedure.id, procedureRevision = procedureRevision,
                    stepId = stepId, toolName = step.tool, status = StepStatus.FAILED,
                    startedAtMs = stepStartMs, completedAtMs = nowMs(), inputDigest = inputDigest,
                    diagnostic = ZeroDiagnostic("ZERO_STEP_FAILED", stepId, err),
                )
                if (firstError == null) firstError = err
                continue
            }
            if (out == null) {
                val err = "step '$stepId': '${step.tool}' exceeded ${step.timeoutSeconds}s"
                stepResults += ZeroStepResult(stepId, false, error = err)
                stepReceipts += ZeroStepReceipt(
                    procedureId = procedure.id, procedureRevision = procedureRevision,
                    stepId = stepId, toolName = step.tool, status = StepStatus.TIMEOUT,
                    startedAtMs = stepStartMs, completedAtMs = nowMs(), inputDigest = inputDigest,
                    diagnostic = ZeroDiagnostic("ZERO_TIMEOUT", stepId, err),
                )
                if (firstError == null) firstError = err
                continue
            }
            val output = extractOutput(out)
            outputs[stepId] = output
            stepResults += ZeroStepResult(stepId, true, output = output)
            stepReceipts += ZeroStepReceipt(
                procedureId = procedure.id, procedureRevision = procedureRevision,
                stepId = stepId, toolName = step.tool, status = StepStatus.SUCCEEDED,
                startedAtMs = stepStartMs, completedAtMs = nowMs(), inputDigest = inputDigest,
                output = StepOutputRef(stepId, OutputPath.Root),
            )
        }

        val success = firstError == null
        val receipt = ZeroProcedureReceipt(
            procedureId = procedure.id,
            revision = procedureRevision,
            status = if (success) ProcedureStatus.SUCCEEDED else ProcedureStatus.FAILED,
            steps = stepReceipts,
            startedAtMs = startedAtMs,
            completedAtMs = nowMs(),
            error = firstError,
        )
        return ZeroExecutionResult(success, stepResults, outputs, firstError) to receipt
    }

    private fun digest(s: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Collect every `{{stepId...}}` reference token present anywhere in [args]. */
    private fun collectRefs(args: JsonObject): List<String> {
        val refs = mutableListOf<String>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> element.values.forEach { walk(it) }
                is JsonArray -> element.forEach { walk(it) }
                is JsonPrimitive -> {
                    if (element.isString) {
                        refPattern.findAll(element.content).forEach { refs += it.groupValues[1] }
                    }
                }
            }
        }
        walk(args)
        return refs
    }

    /**
     * Replace `{{stepId.path}}` templates in [args] using [outputs]. Unresolvable templates
     * (missing step or bad path) resolve to a literal marker string so the step still runs
     * deterministically and can surface the failure in its own output.
     */
    private fun resolveTemplates(args: JsonObject, outputs: Map<String, JsonElement>): JsonObject {
        fun resolveString(value: String): String = refPattern.replace(value) { m ->
            val token = m.groupValues[1]
            val targetStepId = token.substringBefore('.')
            val path = token.substringAfter('.', missingDelimiterValue = "")
            val target = outputs[targetStepId]
                ?: return@replace "{{$token:unresolved}}"
            val valueAtPath = if (path.isEmpty()) target else navigate(target, path)
            valueAtPath?.let { render(it) } ?: "{{$token:missing_path}}"
        }
        fun walk(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.entries.associate { (k, v) -> k to walk(v) })
            is JsonArray -> JsonArray(element.map { walk(it) })
            is JsonPrimitive -> if (element.isString) {
                JsonPrimitive(resolveString(element.content))
            } else {
                element
            }
        }
        return walk(args) as JsonObject
    }

    /** Navigate a dot-path inside a JSON value; null when any segment is missing. */
    private fun navigate(element: JsonElement, path: String): JsonElement? {
        var current: JsonElement = element
        for (segment in path.split('.')) {
            current = when (current) {
                is JsonObject -> current[segment] ?: return null
                is JsonArray -> segment.toIntOrNull()?.let { current.getOrNull(it) } ?: return null
                else -> return null
            }
        }
        return current
    }

    /**
     * Render a referenced value into a template string. String primitives substitute their
     * raw content (no JSON quotes); numbers/booleans their canonical form; objects and
     * arrays their compact JSON.
     */
    private fun render(element: JsonElement): String = when (element) {
        is JsonPrimitive -> element.content
        else -> element.toString()
    }

    /** Collapse a tool's [UIMessagePart] list into one JSON output. */
    private fun extractOutput(parts: List<UIMessagePart>): JsonElement {
        val text = parts.filterIsInstance<UIMessagePart.Text>()
            .joinToString("\n") { it.text }
        if (text.isBlank()) return JsonPrimitive("")
        return runCatching { json.parseToJsonElement(text) }
            .getOrElse { JsonPrimitive(text) }
    }

    companion object { private const val TAG = "ZeroProcedureEngine" }
}
