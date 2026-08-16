package me.rerere.ai.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A typed, serialisable UI specification that a model can emit as structured output
 * (e.g. through a dedicated tool) and that RikkaHub renders natively with Jetpack
 * Compose — rather than as raw text or HTML.
 *
 * The tree is deliberately narrow: components are renderable primitives with no
 * arbitrary styling knobs, so a model can produce a coherent card/panel without
 * being able to emit arbitrary markup. See the Compose renderer
 * (`RikkaUiRenderer` in the app module).
 */
@Serializable
sealed class RikkaUi {

    /** Stacked column of child components. */
    @Serializable
    @SerialName("ui_column")
    data class Column(
        val children: kotlin.collections.List<RikkaUi>,
        val spacing: Int = 8,
        /**
         * Controls the horizontal placement of children within the column.
         * Accepts "top" | "center" | "bottom" (legacy misnomer: it aligns on the
         * cross axis of the column, i.e. horizontally). Kept on the wire as-is so
         * previously emitted trees still decode; [Row] uses the same field name for
         * genuine vertical alignment.
         */
        val verticalAlignment: String = "top",
    ) : RikkaUi()

    /** Horizontal row of child components; [verticalAlignment] aligns children vertically. */
    @Serializable
    @SerialName("ui_row")
    data class Row(
        val children: kotlin.collections.List<RikkaUi>,
        val spacing: Int = 8,
        /** "top" | "center" | "bottom" — aligns children vertically within the row. */
        val verticalAlignment: String = "center",
    ) : RikkaUi()

    /** Plain text run. */
    @Serializable
    @SerialName("ui_text")
    data class Text(
        val content: String,
        val emphasized: Boolean = false,
        val title: Boolean = false,
    ) : RikkaUi()

    /** Actionable primary button. */
    @Serializable
    @SerialName("ui_button")
    data class Button(
        val label: String,
        val action: RikkaUiAction,
    ) : RikkaUi()

    /** Small non-primary chip, optionally actionable. */
    @Serializable
    @SerialName("ui_chip")
    data class Chip(
        val label: String,
        val action: RikkaUiAction? = null,
    ) : RikkaUi()

    /** Remote image. */
    @Serializable
    @SerialName("ui_image")
    data class Image(
        val url: String,
    ) : RikkaUi()

    /** Bulleted list of strings. */
    @Serializable
    @SerialName("ui_list")
    data class List(
        val items: kotlin.collections.List<String>,
    ) : RikkaUi()

    /** Horizontal separator. */
    @Serializable
    @SerialName("ui_divider")
    data object Divider : RikkaUi()

    /** A scoped form container; at most one form per validated tree. */
    @Serializable
    @SerialName("ui_form")
    data class Form(
        val id: String,
        val children: kotlin.collections.List<RikkaUi>,
        val spacing: Int = 8,
        val verticalAlignment: String = "top",
    ) : RikkaUi()

    /** Single-line text input bound to [key] within its enclosing form. */
    @Serializable
    @SerialName("ui_input")
    data class Input(
        val key: String,
        val placeholder: String? = null,
        val label: String? = null,
        val initial: String? = null,
    ) : RikkaUi()

    /** Boolean toggle bound to [key]. */
    @Serializable
    @SerialName("ui_toggle")
    data class Toggle(
        val key: String,
        val label: String,
        val initial: Boolean = false,
    ) : RikkaUi()

    /** Single-choice selector bound to [key]. */
    @Serializable
    @SerialName("ui_select")
    data class Select(
        val key: String,
        val label: String,
        val options: kotlin.collections.List<String>,
        val initial: String? = null,
    ) : RikkaUi()

    /** Progress indicator; indeterminate when [fraction] is null. */
    @Serializable
    @SerialName("ui_progress")
    data class Progress(
        val fraction: Float? = null,
    ) : RikkaUi()

    /** Tappable link that opens [url]. */
    @Serializable
    @SerialName("ui_link")
    data class Link(
        val label: String,
        val url: String,
    ) : RikkaUi()
}

/** The user-invoked action attached to an interactive [RikkaUi] component. */
@Serializable
sealed class RikkaUiAction {

    /** Copy [text] to the system clipboard. */
    @Serializable
    @SerialName("ui_copy")
    data class Copy(val text: String) : RikkaUiAction()

    /** Open [url] in an external browser. */
    @Serializable
    @SerialName("ui_open_url")
    data class OpenUrl(val url: String) : RikkaUiAction()

    /** Submit the enclosing form with [formId]; renders as a user turn. */
    @Serializable
    @SerialName("ui_submit")
    data class Submit(val formId: String) : RikkaUiAction()

    /** Navigate to an allowlisted in-app [destination]. */
    @Serializable
    @SerialName("ui_navigate")
    data class Navigate(val destination: String) : RikkaUiAction()
}
