package me.rerere.locallm.litert.browser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Deterministic observations a browser session emits in response to commands. */
@Serializable
sealed class BrowserObservation {

    @Serializable
    @SerialName("browser_page_loaded")
    data object PageLoaded : BrowserObservation()

    @Serializable
    @SerialName("browser_navigation_started")
    data class NavigationStarted(val url: String) : BrowserObservation()

    @Serializable
    @SerialName("browser_navigation_completed")
    data class NavigationCompleted(val url: String) : BrowserObservation()

    @Serializable
    @SerialName("browser_page_state")
    data class PageState(
        val url: String,
        val title: String? = null,
        val text: String? = null,
        val dom: String? = null,
        val links: List<String> = emptyList(),
    ) : BrowserObservation()

    @Serializable
    @SerialName("browser_snapshot_captured")
    data object SnapshotCaptured : BrowserObservation()

    @Serializable
    @SerialName("browser_action_acknowledged")
    data object ActionAcknowledged : BrowserObservation()

    @Serializable
    @SerialName("browser_page_error")
    data class PageError(val url: String, val detail: String? = null) : BrowserObservation()

    @Serializable
    @SerialName("browser_session_evicted")
    data object SessionEvicted : BrowserObservation()

    @Serializable
    @SerialName("browser_command_refused")
    data class CommandRefused(val reason: String) : BrowserObservation()
}
