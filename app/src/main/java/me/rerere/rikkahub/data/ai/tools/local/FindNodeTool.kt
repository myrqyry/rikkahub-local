package me.rerere.rikkahub.data.ai.tools.local

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.AgentTurnTracker
import me.rerere.rikkahub.data.ai.tools.ToolInvocationContext
import me.rerere.rikkahub.service.ActionLogEntry

private val ALLOWED_BY = setOf("text", "content_description", "view_id_resource_name")

private fun findMatches(
    root: AccessibilityNodeInfo,
    by: String,
    value: String,
): List<AccessibilityNodeInfo> {
    return when (by) {
        "view_id_resource_name" -> root.findAccessibilityNodeInfosByViewId(value).orEmpty()
        "text" -> root.findAccessibilityNodeInfosByText(value).orEmpty().filter {
            it.text?.toString() == value
        }
        "content_description" -> {
            val out = mutableListOf<AccessibilityNodeInfo>()
            fun walk(n: AccessibilityNodeInfo) {
                if (n.contentDescription?.toString() == value) out.add(n)
                for (i in 0 until n.childCount) {
                    n.getChild(i)?.let { walk(it) }
                }
            }
            walk(root)
            out
        }
        else -> emptyList()
    }
}

private fun parseSelector(input: kotlinx.serialization.json.JsonElement): Triple<String?, String?, String?> {
    val by = input.jsonObject["by"]?.jsonPrimitive?.contentOrNull
    val value = input.jsonObject["value"]?.jsonPrimitive?.contentOrNull
    val pkg = input.jsonObject["package_name"]?.jsonPrimitive?.contentOrNull
    return Triple(by, value, pkg)
}

private const val DEFAULT_VERIFY_TIMEOUT_MS = 1_800L
private const val MAX_VERIFY_TIMEOUT_MS = 5_000L
private const val VERIFY_POLL_MS = 150L

/**
 * A fresh, narrow observation. Only the caller's expected text is extracted;
 * do not include the rest of the screen (which may contain private content).
 */
private fun observeUiPostcondition(
    root: AccessibilityNodeInfo?,
    condition: UiPostcondition,
): UiObservedState? {
    root ?: return null
    val visibleMatches = setOfNotNull(condition.expectedText, condition.absentText).filter { value ->
        root.findAccessibilityNodeInfosByText(value).orEmpty().any {
            it.isVisibleToUser && it.text?.toString() == value
        }
    }.toSet()
    return UiObservedState(
        packageName = root.packageName?.toString(),
        visibleTexts = visibleMatches,
    )
}

fun findNodeTool(
    invocationContext: ToolInvocationContext = ToolInvocationContext.EMPTY,
    streamer: InteractiveToolStreamer = InteractiveToolStreamer.NoOp,
): Tool = Tool(
    name = "find_node",
    description = """
        Find accessibility nodes in the active window matching a selector. by: text |
        content_description | view_id_resource_name. Returns {matches: [...]} with at most 50
        node summaries. Use read_window_tree first to see what's available, then this for
        targeted lookups.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("by", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("text"); add("content_description"); add("view_id_resource_name") })
                    put("description", "Selector axis")
                })
                put("value", buildJsonObject {
                    put("type", "string")
                    put("description", "Exact value to match")
                })
                put("package_name", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional foreground package guard")
                })
            },
            required = listOf("by", "value")
        )
    },
    execute = { input ->
        me.rerere.rikkahub.service.RikkaAccessibilityService.instance?.let { wakeScreenIfNeeded(it) }
        val (by, value, pkgFilter) = parseSelector(input)
        if (by == null || by !in ALLOWED_BY || value == null) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "by must be one of [text, content_description, view_id_resource_name] and value is required")
                    }.toString()
                )
            )
        }
        val payload = AccessibilityServiceHandle.withService { svc ->
            val root = svc.rootInActiveWindow
            if (root == null) {
                return@withService buildJsonObject {
                    put("error", "no_active_window")
                    put("matches", buildJsonArray {})
                }
            }
            val pkg = root.packageName?.toString().orEmpty()
            if (pkgFilter != null && pkgFilter != pkg) {
                return@withService buildJsonObject {
                    put("error", "wrong_foreground_app")
                    put("current", pkg)
                    put("matches", buildJsonArray {})
                }
            }
            val matches = findMatches(root, by, value).take(50)
            svc.appendLog(
                ActionLogEntry(
                    type = "find_node",
                    paramsSummary = "$by=\"${value.take(40)}\" -> ${matches.size}",
                    success = true,
                    timestampMs = System.currentTimeMillis(),
                )
            )
            buildJsonObject {
                put("matches", buildJsonArray {
                    matches.forEachIndexed { i, n -> add(nodeToJson(n, root.windowId, i)) }
                })
            }
        }
        streamer.streamIfHeadless(invocationContext, "FindNode $by=\"${value.take(30)}\"")
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

fun clickNodeTool(
    invocationContext: ToolInvocationContext = ToolInvocationContext.EMPTY,
    streamer: InteractiveToolStreamer = InteractiveToolStreamer.NoOp,
): Tool = Tool(
    name = "click_node",
    description = """
        Find an accessibility node by selector and tap it via ACTION_CLICK. If the matched node
        is not clickable but a clickable ancestor exists, the ancestor is clicked instead.
        nth (default 0) disambiguates when multiple nodes match. success means Android
        accepted ACTION_CLICK, NOT that the user's goal was reached. Optionally supply
        expect_text (exact visible text to appear), expect_absent_text (text to disappear),
        and/or expect_package (foreground package) to verify the result from fresh
        accessibility observations after the click. Verification has a bounded
        verify_timeout_ms (default 1800, max 5000). verification.status reports passed,
        already_satisfied, not_observed, action_rejected, or not_requested.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("by", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("text"); add("content_description"); add("view_id_resource_name") })
                })
                put("value", buildJsonObject { put("type", "string") })
                put("package_name", buildJsonObject { put("type", "string") })
                put("nth", buildJsonObject {
                    put("type", "integer")
                    put("description", "Zero-based index when multiple nodes match (default 0)")
                })
                put("expect_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Exact visible accessibility text expected after the click")
                })
                put("expect_absent_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Exact accessibility text expected to disappear after the click")
                })
                put("expect_package", buildJsonObject {
                    put("type", "string")
                    put("description", "Foreground app package expected after the click")
                })
                put("verify_timeout_ms", buildJsonObject {
                    put("type", "integer")
                    put("minimum", 0)
                    put("maximum", MAX_VERIFY_TIMEOUT_MS)
                    put("description", "Postcondition observation budget in milliseconds (default 1800)")
                })
            },
            required = listOf("by", "value")
        )
    },
    execute = { input ->
        AgentTurnTracker.recordAutomationAction()
        me.rerere.rikkahub.service.RikkaAccessibilityService.instance?.let { wakeScreenIfNeeded(it) }
        val (by, value, pkgFilter) = parseSelector(input)
        val nth = input.jsonObject["nth"]?.jsonPrimitive?.intOrNull ?: 0
        val expectText = input.jsonObject["expect_text"]?.jsonPrimitive?.contentOrNull
        val expectAbsentText = input.jsonObject["expect_absent_text"]?.jsonPrimitive?.contentOrNull
        val expectPackage = input.jsonObject["expect_package"]?.jsonPrimitive?.contentOrNull
        val timeoutField = input.jsonObject["verify_timeout_ms"]
        val verifyTimeout = timeoutField?.jsonPrimitive?.longOrNull ?: DEFAULT_VERIFY_TIMEOUT_MS
        val expectationFields = listOfNotNull(expectText, expectAbsentText, expectPackage)
        if (expectationFields.any { it.isBlank() } || (expectText != null && expectText == expectAbsentText)) {
            return@Tool listOf(UIMessagePart.Text(
                buildJsonObject { put("error", "invalid_verification_expectation") }.toString()
            ))
        }
        val expectation = if (expectationFields.isNotEmpty()) {
            UiPostcondition(expectText, expectAbsentText, expectPackage)
        } else null
        if ((timeoutField != null && timeoutField.jsonPrimitive.longOrNull == null) ||
            verifyTimeout !in 0L..MAX_VERIFY_TIMEOUT_MS ||
            (timeoutField != null && expectation == null)
        ) {
            return@Tool listOf(UIMessagePart.Text(
                buildJsonObject { put("error", "invalid_verify_timeout_or_missing_expectation") }.toString()
            ))
        }
        if (by == null || by !in ALLOWED_BY || value == null) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "by must be one of [text, content_description, view_id_resource_name] and value is required")
                    }.toString()
                )
            )
        }
        if (nth < 0) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "nth must be >= 0")
                    }.toString()
                )
            )
        }
        val payload = AccessibilityServiceHandle.withService { svc ->
            val root = svc.rootInActiveWindow
            if (root == null) {
                return@withService buildJsonObject { put("error", "no_active_window") }
            }
            val pkg = root.packageName?.toString().orEmpty()
            if (pkgFilter != null && pkgFilter != pkg) {
                return@withService buildJsonObject {
                    put("error", "wrong_foreground_app")
                    put("current", pkg)
                }
            }
            val matches = findMatches(root, by, value)
            if (matches.isEmpty()) {
                return@withService buildJsonObject { put("error", "no_match") }
            }
            if (nth >= matches.size) {
                return@withService buildJsonObject {
                    put("error", "nth_out_of_range")
                    put("available", matches.size)
                }
            }
            val target = matches[nth]
            val clickable = svc.resolveClickable(target)
                ?: return@withService buildJsonObject { put("error", "no_clickable_ancestor") }
            // Capture the original target before navigation invalidates accessibility nodes.
            val clickedBounds = android.graphics.Rect().apply { clickable.getBoundsInScreen(this) }
            val clickedText = clickable.text?.toString().orEmpty()
            val resolvedViaAncestor = clickable !== target
            // ACTION_CLICK returning true only acknowledges dispatch. Independently
            // observe the next state when the caller supplies an explicit postcondition.
            val initiallySatisfied = expectation?.let { it.matches(observeUiPostcondition(root, it)) } ?: false
            val ok = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            var lastObserved: UiObservedState? = null
            var verifyElapsedMs = 0L
            if (expectation != null && ok) {
                val startMs = SystemClock.elapsedRealtime()
                do {
                    lastObserved = observeUiPostcondition(svc.rootInActiveWindow, expectation)
                    if (expectation.matches(lastObserved)) break
                    val elapsedMs = SystemClock.elapsedRealtime() - startMs
                    if (elapsedMs >= verifyTimeout) break
                    delay(minOf(VERIFY_POLL_MS, verifyTimeout - elapsedMs))
                } while (true)
                verifyElapsedMs = SystemClock.elapsedRealtime() - startMs
            }
            val verificationStatus = expectation?.let {
                assessUiPostcondition(
                    actionAccepted = ok,
                    initiallySatisfied = initiallySatisfied,
                    observedSatisfied = it.matches(lastObserved),
                )
            }
            svc.appendLog(
                ActionLogEntry(
                    type = "click_node",
                    paramsSummary = "$by=\"${value.take(40)}\" nth=$nth -> ${if (ok) "ok" else "fail"}",
                    success = ok,
                    timestampMs = System.currentTimeMillis(),
                )
            )
            buildJsonObject {
                put("success", ok)
                put("verification", buildJsonObject {
                    put("status", verificationStatus?.wireValue ?: "not_requested")
                    put("postcondition_verified", verificationStatus == UiVerificationStatus.PASSED)
                    if (expectation != null) {
                        put("initially_satisfied", initiallySatisfied)
                        put("criteria_met_after", expectation.matches(lastObserved))
                        put("observation_available", lastObserved != null)
                        lastObserved?.packageName?.let { put("observed_package", it) }
                        put("elapsed_ms", verifyElapsedMs)
                    }
                })
                put("clicked", buildJsonObject {
                    put("bounds", buildJsonArray {
                        add(clickedBounds.left); add(clickedBounds.top)
                        add(clickedBounds.right); add(clickedBounds.bottom)
                    })
                    put("text", clickedText)
                    put("resolved_via_ancestor", resolvedViaAncestor)
                })
            }
        }
        streamer.streamIfHeadless(invocationContext, "ClickNode $by=\"${value.take(30)}\"")
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

/**
 * Type text into an editable accessibility node by selector. Works on standard input fields
 * (URL bars, search boxes, form fields, IME-driven text views). Does NOT work on terminals
 * like Termux because they render to a Surface and do not expose editable nodes; for Termux
 * use termux_run_command instead.
 */
fun setTextTool(
    invocationContext: ToolInvocationContext = ToolInvocationContext.EMPTY,
    streamer: InteractiveToolStreamer = InteractiveToolStreamer.NoOp,
): Tool = Tool(
    name = "set_text",
    description = """
        Type or replace text in an editable input field on screen. Find the field by selector
        (text / content_description / view_id_resource_name). Works for URL bars, search boxes,
        form fields. Does NOT work for terminals like Termux that render natively - for Termux
        run shell commands directly via termux_run_command. Returns {success, set_to, ...} or
        a structured error.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("by", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("text"); add("content_description"); add("view_id_resource_name") })
                    put("description", "Selector axis to find the editable node")
                })
                put("value", buildJsonObject {
                    put("type", "string")
                    put("description", "Selector value to match against")
                })
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "The text to set on the matched node")
                })
                put("package_name", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional foreground package guard")
                })
                put("nth", buildJsonObject {
                    put("type", "integer")
                    put("description", "Zero-based index when multiple nodes match (default 0)")
                })
            },
            required = listOf("by", "value", "text")
        )
    },
    execute = { input ->
        AgentTurnTracker.recordAutomationAction()
        me.rerere.rikkahub.service.RikkaAccessibilityService.instance?.let { wakeScreenIfNeeded(it) }
        val (by, value, pkgFilter) = parseSelector(input)
        val nth = input.jsonObject["nth"]?.jsonPrimitive?.intOrNull ?: 0
        val newText = input.jsonObject["text"]?.jsonPrimitive?.contentOrNull
        if (by == null || by !in ALLOWED_BY || value == null || newText == null) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "by, value, and text are required; by must be one of [text, content_description, view_id_resource_name]")
                    }.toString()
                )
            )
        }
        if (nth < 0) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject { put("error", "nth must be >= 0") }.toString()
                )
            )
        }
        val payload = AccessibilityServiceHandle.withService { svc ->
            val root = svc.rootInActiveWindow
                ?: return@withService buildJsonObject { put("error", "no_active_window") }
            val pkg = root.packageName?.toString().orEmpty()
            if (pkgFilter != null && pkgFilter != pkg) {
                return@withService buildJsonObject {
                    put("error", "wrong_foreground_app")
                    put("current", pkg)
                }
            }
            val matches = findMatches(root, by, value)
            if (matches.isEmpty()) {
                return@withService buildJsonObject { put("error", "no_match") }
            }
            if (nth >= matches.size) {
                return@withService buildJsonObject {
                    put("error", "nth_out_of_range")
                    put("available", matches.size)
                }
            }
            val target = matches[nth]
            // ACTION_SET_TEXT requires the node to be editable. Walk up the parent chain
            // looking for one - some apps wrap their EditText in a non-editable container.
            var editable: AccessibilityNodeInfo? = target
            while (editable != null && !editable.isEditable) {
                editable = editable.parent
            }
            if (editable == null) {
                return@withService buildJsonObject {
                    put("error", "node_not_editable")
                    put("recovery", "The matched node is not an editable input. Terminals (Termux) render natively and do not expose editable nodes; use termux_run_command instead.")
                }
            }
            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    newText
                )
            }
            val ok = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            svc.appendLog(
                ActionLogEntry(
                    type = "set_text",
                    paramsSummary = "$by=\"${value.take(30)}\" -> \"${newText.take(30)}\"",
                    success = ok,
                    timestampMs = System.currentTimeMillis(),
                )
            )
            buildJsonObject {
                put("success", ok)
                if (!ok) put("reason", "action_rejected")
                put("set_to", newText)
            }
        }
        streamer.streamIfHeadless(invocationContext, "SetText $by=\"${value.take(20)}\" -> \"${newText.take(20)}\"")
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
