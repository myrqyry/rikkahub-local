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
        /** "top" | "center" | "bottom" */
        val verticalAlignment: String = "top",
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
}
