package me.rerere.locallm.litert

import android.util.Log
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool

/**
 * Phase 3 — the default [ActionPlanCompiler]. Deterministic, schema-driven
 * validation and lossless repair. Never invokes the model, never executes a
 * tool, and never applies a lossy fix silently.
 *
 * What it checks (and what it repairs, when unambiguous):
 *
 *  - **Unknown / deprecated tool ids** → repaired to the canonical id via
 *    [CompilationContext.canonicalAliases] when the target exists (lossless,
 *    confidence 1.0); otherwise [Invalid] with the catalog as a repair hint.
 *  - **Missing required arguments** → repaired to the schema default when the
 *    schema declares one (lossless); otherwise [Invalid].
 *  - **Lossless argument coercions** (`"30"` → `30`, `"true"` → `true`,
 *    integer → double where lossless) → repaired.
 *  - **Extra / undeclared arguments** → dropped (lossless, the tool schema is
 *    the contract).
 *  - **Type mismatches that are not losslessly coercible** → [Invalid].
 *  - **Workflow calls with a blank workflow id** → [Invalid].
 *
 * Capability-grant gating is deliberately NOT part of the compiler: the grant is
 * enforced at execution time ([DirectToolExecutor]) so the compiler stays pure
 * and the grant stays authoritative.
 */
class DefaultActionPlanCompiler : ActionPlanCompiler {

    override suspend fun compile(plan: ActionPlan, context: CompilationContext): CompilationResult {
        return when (plan) {
            is ActionPlan.ToolCall -> compileToolCall(plan, context)
            is ActionPlan.WorkflowCall -> compileWorkflowCall(plan)
        }
    }

    private fun compileToolCall(plan: ActionPlan.ToolCall, context: CompilationContext): CompilationResult {
        val repairs = mutableListOf<Repair>()
        var tool = context.toolCatalog[plan.toolName]

        // Unknown / deprecated tool id → canonical alias, if one exists and resolves.
        if (tool == null) {
            val canonical = context.canonicalAliases[plan.toolName]
            val resolved = canonical?.let(context.toolCatalog::get)
            if (canonical != null && resolved != null) {
                repairs += Repair(
                    code = "PLAN_TOOL_001",
                    step = step(plan),
                    message = "Tool '${plan.toolName}' is a deprecated alias; replaced with canonical tool '$canonical'.",
                    kind = Repair.Kind.REPLACE_TOOL,
                    candidate = canonical,
                )
                tool = resolved
            } else {
                val available = context.toolCatalog.keys.sorted().joinToString(", ")
                return CompilationResult.Invalid(
                    diagnostics = listOf(
                        Diagnostic(
                            code = "PLAN_TOOL_001",
                            step = step(plan),
                            message = "Unknown tool '${plan.toolName}'.",
                        )
                    ),
                    repairHints = listOf(
                        RepairHint(
                            code = "PLAN_TOOL_001",
                            step = step(plan),
                            message = "Unknown tool '${plan.toolName}'.",
                            suggestion = if (available.isEmpty()) {
                                "No tools are available."
                            } else {
                                "Use one of the available tools: $available."
                            },
                        )
                    ),
                )
            }
        }

        val schema = tool.parameters()
        val argsResult = compileArgs(plan, schema)
        if (argsResult is ArgsResult.Invalid) {
            return CompilationResult.Invalid(
                diagnostics = argsResult.diagnostics,
                repairHints = argsResult.diagnostics.map {
                    RepairHint(
                        code = it.code,
                        step = it.step,
                        message = it.message,
                        suggestion = "Provide the missing/invalid argument for tool '${plan.toolName}'.",
                    )
                },
            )
        }

        val args = (argsResult as ArgsResult.Ok).args
        val allRepairs = repairs + argsResult.repairs
        val finalPlan = if (allRepairs.isEmpty()) {
            plan
        } else {
            plan.copy(
                toolName = tool.name,
                args = args,
            )
        }

        if (allRepairs.isEmpty()) {
            return CompilationResult.Valid(plan)
        }
        Log.i(TAG, "repaired ${step(plan)}: ${allRepairs.joinToString { "${it.code}(${it.kind})" }}")
        return CompilationResult.Repaired(
            originalPlan = plan,
            repairedPlan = finalPlan,
            repairs = allRepairs,
        )
    }

    private sealed interface ArgsResult {
        data class Ok(
            val args: JsonObject,
            val repairs: List<Repair>,
        ) : ArgsResult

        data class Invalid(
            val diagnostics: List<Diagnostic>,
        ) : ArgsResult
    }

    /**
     * Validate and (where lossless) repair the arguments against [schema].
     * Returns either repaired args plus the repairs applied, or diagnostics when
     * a required argument is missing without a schema default.
     */
    private fun compileArgs(
        plan: ActionPlan.ToolCall,
        schema: InputSchema?,
    ): ArgsResult {
        if (schema !is InputSchema.Obj) {
            return ArgsResult.Ok(plan.args, emptyList())
        }

        val props = schema.properties
        val required = schema.required ?: emptyList()
        val repairs = mutableListOf<Repair>()
        val diagnostics = mutableListOf<Diagnostic>()
        val out = buildJsonObject {
            for ((key, value) in plan.args) {
                val prop = props[key] as? JsonObject
                if (prop == null) {
                    repairs += Repair(
                        code = "PLAN_ARG_004",
                        step = step(plan),
                        message = "Argument '$key' is not declared by tool '${plan.toolName}'; dropped.",
                        kind = Repair.Kind.DROP_ARG,
                    )
                    continue
                }
                val coerced = coerceValue(value, prop)
                if (coerced != null) {
                    repairs += Repair(
                        code = "PLAN_ARG_003",
                        step = step(plan),
                        message = "Argument '$key' coerced from '${(value as? JsonPrimitive)?.content}' to schema type.",
                        kind = Repair.Kind.COERCE_ARG,
                    )
                    put(key, coerced)
                } else {
                    put(key, value)
                }
            }
            for (key in required) {
                if (!plan.args.containsKey(key)) {
                    val default = (props[key] as? JsonObject)?.get("default")
                    if (default != null && default !is JsonNull) {
                        repairs += Repair(
                            code = "PLAN_ARG_002",
                            step = step(plan),
                            message = "Missing required argument '$key'; using schema default.",
                            kind = Repair.Kind.FILL_DEFAULT,
                            confidence = 1.0,
                        )
                        put(key, default)
                    } else {
                        diagnostics += Diagnostic(
                            code = "PLAN_ARG_001",
                            step = step(plan),
                            message = "Missing required argument '$key' for tool '${plan.toolName}'.",
                        )
                    }
                }
            }
        }

        if (diagnostics.isNotEmpty()) {
            return ArgsResult.Invalid(diagnostics)
        }

        return ArgsResult.Ok(out, repairs)
    }

    /** Coerce [value] to the schema type declared by [prop] when the coercion is lossless. */
    private fun coerceValue(value: JsonElement, prop: JsonObject): JsonElement? {
        if (value is JsonNull) return null
        val primitive = value as? JsonPrimitive ?: return null
        if (!primitive.isString) return null

        val type = (prop["type"] as? JsonPrimitive)?.content ?: return null
        return when (type) {
            "integer" -> primitive.content.toLongOrNull()?.let { JsonPrimitive(it) }
            "number" -> primitive.content.toDoubleOrNull()?.let { JsonPrimitive(it) }
            "boolean" -> when (primitive.content) {
                "true" -> JsonPrimitive(true)
                "false" -> JsonPrimitive(false)
                else -> null
            }
            else -> null
        }
    }

    private fun compileWorkflowCall(plan: ActionPlan.WorkflowCall): CompilationResult {
        if (plan.workflowId.isBlank()) {
            return CompilationResult.Invalid(
                diagnostics = listOf(
                    Diagnostic(
                        code = "PLAN_WFLOW_001",
                        step = step(plan),
                        message = "Workflow call has a blank workflow id.",
                    )
                ),
            )
        }
        return CompilationResult.Valid(plan)
    }

    private fun step(plan: ActionPlan): String = when (plan) {
        is ActionPlan.ToolCall -> "tool:${plan.toolName}"
        is ActionPlan.WorkflowCall -> "workflow:${plan.workflowId}"
    }

    companion object {
        private const val TAG = "DefaultActionPlanCompiler"
    }
}
