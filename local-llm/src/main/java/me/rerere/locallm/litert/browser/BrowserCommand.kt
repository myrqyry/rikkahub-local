package me.rerere.locallm.litert.browser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Deterministic, typed browser commands. Wire discriminators are part of the
 * contract — never rename them.
 */
@Serializable
sealed class BrowserCommand {

    @Serializable
    @SerialName("browser_navigate")
    data class Navigate(val url: String) : BrowserCommand()

    @Serializable
    @SerialName("browser_click")
    data class Click(val selector: String) : BrowserCommand()

    @Serializable
    @SerialName("browser_type")
    data class Type(val selector: String, val text: String) : BrowserCommand()

    @Serializable
    @SerialName("browser_scroll")
    data class Scroll(val direction: String, val amount: Int) : BrowserCommand()

    @Serializable
    @SerialName("browser_back")
    data object Back : BrowserCommand()

    @Serializable
    @SerialName("browser_forward")
    data object Forward : BrowserCommand()

    @Serializable
    @SerialName("browser_submit")
    data class Submit(val selector: String) : BrowserCommand()

    @Serializable
    @SerialName("browser_select")
    data class Select(val selector: String, val value: String) : BrowserCommand()

    @Serializable
    @SerialName("browser_wait_for")
    data class WaitFor(val selector: String, val state: String, val containsText: String? = null) : BrowserCommand()

    @Serializable
    @SerialName("browser_snapshot")
    data object Snapshot : BrowserCommand()

    @Serializable
    @SerialName("browser_eval_js")
    data class EvalJs(val script: String) : BrowserCommand()

    @Serializable
    @SerialName("browser_done")
    data object Done : BrowserCommand()
}
