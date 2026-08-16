package me.rerere.rikkahub.ui.components.message

import kotlinx.serialization.json.Json
import me.rerere.ai.ui.DEFAULT_NAV_DESTINATIONS
import me.rerere.ai.ui.UIMessagePart

/**
 * A typed user event originating from an interactive RikkaUi projection.
 * Converted to model-facing text at the chat ingress (Task 7) — never persisted as a
 * UIMessagePart type, and never passed through provider adapters.
 */
sealed interface RikkaUiEvent {
    data class FormSubmit(
        val renderId: String,
        val formId: String,
        val values: Map<String, String>,
    ) : RikkaUiEvent
}

/** Canonical model-facing text for a [RikkaUiEvent.FormSubmit], values keys sorted. */
fun formSubmitToText(event: RikkaUiEvent.FormSubmit): String {
    val sorted: Map<String, String> = event.values.toSortedMap()
    return buildString {
        append("""{"type":"rikka_ui_form_submit","renderId":""")
        append(Json.encodeToString(event.renderId))
        append(""","formId":""")
        append(Json.encodeToString(event.formId))
        append(""","values":""")
        append(Json.encodeToString(sorted))
        append("}")
    }
}

/** Wraps a [RikkaUiEvent.FormSubmit] as an ordinary new user turn. */
fun formSubmitToUserTurn(event: RikkaUiEvent.FormSubmit): List<UIMessagePart> =
    listOf(UIMessagePart.Text(formSubmitToText(event)))

/**
 * Resolves a model-authored navigation destination against an allowlist.
 * Returns null when the destination is not allowlisted, so a model can never
 * drive the [androidx.navigation.NavController] directly.
 */
fun resolveNavigationDestination(
    destination: String,
    allowlist: Set<String> = DEFAULT_NAV_DESTINATIONS,
): String? = destination.takeIf { it in allowlist }
