package me.rerere.locallm.litert.browser

import kotlinx.serialization.Serializable

/** Opaque reference to a browser session. Never exposes raw ids. */
@Serializable
data class BrowserRef(val id: String) {
    override fun toString(): String = "browser:$id"
}
