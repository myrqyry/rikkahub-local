package me.rerere.locallm.litert.browser

import me.rerere.locallm.litert.CapabilityGrant
import java.net.URI

/** The capability/effect gate for browser effects. Refusals are never thrown. */
data class BrowserDecision(
    val allowed: Boolean,
    val reason: String? = null,
    val effect: BrowserEffect? = null,
)

class BrowserGate {

    /** Refusal code for unsafe navigation targets. */
    val navigationDeniedReason = "browser_navigation_denied"

    /** Refusal code for eval without a grant. */
    val evalJsDeniedReason = "browser_eval_js_denied"

    fun evaluate(command: BrowserCommand, granted: CapabilityGrant?): BrowserDecision = when (command) {
        is BrowserCommand.Navigate ->
            if (isSafeUrl(command.url)) BrowserDecision(true, effect = BrowserEffect.NAVIGATE)
            else BrowserDecision(false, navigationDeniedReason)

        is BrowserCommand.EvalJs ->
            if (granted?.isAllowed("browser_eval_js") == true) BrowserDecision(true, effect = BrowserEffect.EVAL_JS)
            else BrowserDecision(false, evalJsDeniedReason)

        is BrowserCommand.Done -> BrowserDecision(true, effect = BrowserEffect.DONE)
        is BrowserCommand.Click -> BrowserDecision(true, effect = BrowserEffect.CLICK)
        is BrowserCommand.Type -> BrowserDecision(true, effect = BrowserEffect.TYPE)
        is BrowserCommand.Scroll -> BrowserDecision(true, effect = BrowserEffect.SCROLL)
        is BrowserCommand.Back -> BrowserDecision(true, effect = BrowserEffect.BACK)
        is BrowserCommand.Forward -> BrowserDecision(true, effect = BrowserEffect.FORWARD)
        is BrowserCommand.Submit -> BrowserDecision(true, effect = BrowserEffect.SUBMIT)
        is BrowserCommand.Select -> BrowserDecision(true, effect = BrowserEffect.SELECT)
        is BrowserCommand.WaitFor -> BrowserDecision(true, effect = BrowserEffect.WAIT_FOR)
        is BrowserCommand.Snapshot -> BrowserDecision(true, effect = BrowserEffect.SNAPSHOT)
    }

    private fun isSafeUrl(url: String): Boolean = try {
        URI(url).scheme in SAFE_URL_SCHEMES
    } catch (e: Exception) {
        false
    }

    private companion object {
        val SAFE_URL_SCHEMES = setOf("http", "https", "file", "content")
    }
}
