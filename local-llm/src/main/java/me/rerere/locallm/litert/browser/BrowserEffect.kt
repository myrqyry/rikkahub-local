package me.rerere.locallm.litert.browser

/** Stable effect names a browser backend must implement. */
enum class BrowserEffect {
    NAVIGATE,
    CLICK,
    TYPE,
    SCROLL,
    BACK,
    FORWARD,
    SUBMIT,
    SELECT,
    WAIT_FOR,
    SNAPSHOT,
    EVAL_JS,
    DONE,
}
