package me.rerere.rikkahub.ui.components.message

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.ai.ui.RikkaUi
import me.rerere.ai.ui.RikkaUiAction
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage

/**
 * Renders a typed [RikkaUi] tree emitted by a model as structured output.
 * Each component maps to a narrow Compose primitive — no arbitrary markup.
 */
@Composable
fun RikkaUiRenderer(
    ui: RikkaUi,
    modifier: Modifier = Modifier,
) {
    when (ui) {
        is RikkaUi.Column -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ui.spacing.dp),
                horizontalAlignment = when (ui.verticalAlignment) {
                    "center" -> Alignment.CenterHorizontally
                    "bottom" -> Alignment.End
                    else -> Alignment.Start
                },
            ) {
                for (child in ui.children) {
                    RikkaUiRenderer(child)
                }
            }
        }

        is RikkaUi.Text -> {
            Text(
                text = ui.content,
                style = if (ui.title) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (ui.emphasized) FontWeight.Bold else null,
            )
        }

        is RikkaUi.Button -> {
            val clipboard = LocalClipboardManager.current
            val context = LocalContext.current
            Button(
                onClick = { handleAction(ui.action, clipboard, context) },
            ) {
                Text(ui.label)
            }
        }

        is RikkaUi.Chip -> {
            val clipboard = LocalClipboardManager.current
            val context = LocalContext.current
            AssistChip(
                onClick = ui.action?.let { { handleAction(it, clipboard, context) } } ?: {},
                enabled = ui.action != null,
                label = { Text(ui.label) },
            )
        }

        is RikkaUi.Image -> {
            ZoomableAsyncImage(
                model = ui.url,
                contentDescription = null,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .height(180.dp),
            )
        }

        is RikkaUi.List -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (item in ui.items) {
                    Text(
                        text = "• $item",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        is RikkaUi.Divider -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

private fun handleAction(action: RikkaUiAction, clipboard: ClipboardManager, context: Context) {    when (action) {
        is RikkaUiAction.Copy -> {
            clipboard.setText(AnnotatedString(action.text))
        }

        is RikkaUiAction.OpenUrl -> {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)))
        }
    }
}
