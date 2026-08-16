package me.rerere.ai.ui

import java.net.URI

const val RIKKAUi_MAX_DEPTH = 6
const val RIKKAUi_MAX_NODES = 100

val DEFAULT_NAV_DESTINATIONS: Set<String> = setOf("/images", "/gallery", "/files", "/workspace")

fun validateRikkaUi(ui: RikkaUi): List<String> {
    val errors = mutableListOf<String>()
    val keys = mutableSetOf<String>()
    var formId: String? = null
    var nodeCount = 0

    fun walk(node: RikkaUi, depth: Int) {
        nodeCount++
        if (depth > RIKKAUi_MAX_DEPTH) errors += "depth exceeds $RIKKAUi_MAX_DEPTH"
        when (node) {
            is RikkaUi.Column -> {
                if (node.spacing !in 0..32) errors += "column spacing ${node.spacing} out of 0..32"
                node.children.forEach { walk(it, depth + 1) }
            }
            is RikkaUi.Form -> {
                if (formId != null) errors += "only one Form per tree is allowed"
                formId = node.id
                if (node.spacing !in 0..32) errors += "form spacing ${node.spacing} out of 0..32"
                node.children.forEach { walk(it, depth + 1) }
            }
            is RikkaUi.Row -> {
                if (node.spacing !in 0..32) errors += "row spacing ${node.spacing} out of 0..32"
                node.children.forEach { walk(it, depth + 1) }
            }
            is RikkaUi.Input -> if (!keys.add(node.key)) errors += "key ${node.key} not unique"
            is RikkaUi.Toggle -> if (!keys.add(node.key)) errors += "key ${node.key} not unique"
            is RikkaUi.Select -> {
                if (!keys.add(node.key)) errors += "key ${node.key} not unique"
                if (node.options.isEmpty()) errors += "select ${node.key} needs at least one option"
                if (node.options.size != node.options.toSet().size) errors += "select ${node.key} options must be distinct"
                if (node.initial != null && node.initial !in node.options) errors += "select ${node.key} initial not in options"
            }
            is RikkaUi.Progress -> {
                val f = node.fraction
                if (f != null && (f < 0f || f > 1f)) errors += "progress fraction $f out of 0f..1f"
            }
            is RikkaUi.Image -> if (!isSafeUrl(node.url)) errors += "unsafe url scheme in image"
            is RikkaUi.Link -> if (!isSafeUrl(node.url)) errors += "unsafe url scheme in link"
            is RikkaUi.Text -> {}
            is RikkaUi.Button -> validateAction(node.action, errors)
            is RikkaUi.Chip -> node.action?.let { validateAction(it, errors) }
            is RikkaUi.List -> {}
            is RikkaUi.Divider -> {}
        }
    }

    walk(ui, 0)
    if (nodeCount > RIKKAUi_MAX_NODES) errors += "node count $nodeCount exceeds $RIKKAUi_MAX_NODES"
    return errors
}

private fun isSafeUrl(url: String): Boolean = try {
    val scheme = URI(url).scheme
    scheme in setOf("http", "https", "file", "content")
} catch (_: Exception) {
    false
}

private fun validateAction(action: RikkaUiAction, errors: MutableList<String>) {
    if (action is RikkaUiAction.Navigate && action.destination !in DEFAULT_NAV_DESTINATIONS) {
        errors += "navigate destination ${action.destination} not allowlisted"
    }
}
