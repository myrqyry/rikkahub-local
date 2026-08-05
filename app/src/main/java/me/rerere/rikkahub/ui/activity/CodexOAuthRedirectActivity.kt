package me.rerere.rikkahub.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import me.rerere.rikkahub.RouteActivity

class CodexOAuthRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri?.scheme != "rikkahub" || uri.host != "codex" || uri.path != "/oauth") {
            finish()
            return
        }
        startActivity(
            Intent(this, RouteActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(RouteActivity.EXTRA_OPEN_CODEX_SETTINGS, true)
            }
        )
        finish()
    }
}
