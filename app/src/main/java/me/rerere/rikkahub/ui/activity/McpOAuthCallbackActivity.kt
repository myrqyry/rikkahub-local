package me.rerere.rikkahub.ui.activity

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import org.koin.android.ext.android.inject

/**
 * 透明 Activity，用于接收 MCP OAuth 授权完成后的 deep link 回调
 * (rikkahub://mcp-oauth-callback?code=...&state=...)，解析后经 [AppEventBus] 转发。
 */
class McpOAuthCallbackActivity : ComponentActivity() {
    private val eventBus by inject<AppEventBus>()
    private val appScope by inject<AppScope>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleCallback(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallback(intent)
    }

    private fun handleCallback(source: Intent?) {
        val uri = source?.data?.takeIf(::isExpectedCallback) ?: return
        val state = uri.getQueryParameter("state")?.takeIf { it.length in 1..512 } ?: return
        val code = uri.getQueryParameter("code")?.takeIf { it.length in 1..8192 }
        val error = uri.getQueryParameter("error")?.takeIf { it.length <= 256 }
        if (code == null && error == null) return
        appScope.launch {
            eventBus.emit(AppEvent.McpOAuthCallback(state = state, code = code, error = error))
        }
    }

    private fun isExpectedCallback(uri: Uri): Boolean =
        uri.scheme == "rikkahub" && uri.host == "mcp-oauth-callback"
}
