package me.rerere.rikkahub.ui.components.message

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * Seeds the root form-state map from the initial values of interactive nodes.
 */
fun seedFrom(ui: RikkaUi): Map<String, String> {
    val out = mutableMapOf<String, String>()
    fun walk(node: RikkaUi) {
        when (node) {
            is RikkaUi.Form -> node.children.forEach(::walk)
            is RikkaUi.Column -> node.children.forEach(::walk)
            is RikkaUi.Row -> node.children.forEach(::walk)
            is RikkaUi.Input -> out[node.key] = node.initial ?: ""
            is RikkaUi.Toggle -> out[node.key] = node.initial.toString()
            is RikkaUi.Select -> out[node.key] = node.initial ?: ""
            else -> {}
        }
    }
    walk(ui)
    return out
}

/** Saves/restores the root form-state map across configuration changes and process death. */
val formValuesSaver = listSaver<MutableState<Map<String, String>>, Map<String, String>>(
    save = { state -> listOf(state.value) },
    restore = { list -> mutableStateOf(list.first()) },
)

/**
 * Renders a typed [RikkaUi] tree emitted by a model as structured output.
 * Each component maps to a narrow Compose primitive — no arbitrary markup.
 * One root state map per instance, keyed by the stable [renderId] (the originating
 * toolCallId). Input changes update local state per keystroke; no chat event is
 * emitted until [onSubmit] fires.
 */
@Composable
fun RikkaUiRenderer(
    ui: RikkaUi,
    renderId: String,
    onSubmit: (RikkaUiEvent.FormSubmit) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var values by rememberSaveable(renderId, saver = formValuesSaver) { mutableStateOf(seedFrom(ui)) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val submit: (RikkaUiAction) -> Unit = { action ->
        handleAction(action, renderId, values, onSubmit, onNavigate, clipboard, context)
    }

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
                    RikkaUiRenderer(child, renderId, onSubmit, onNavigate)
                }
            }
        }

        is RikkaUi.Form -> {
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
                    RikkaUiRenderer(child, renderId, onSubmit, onNavigate)
                }
            }
        }

        is RikkaUi.Row -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ui.spacing.dp),
                verticalAlignment = when (ui.verticalAlignment) {
                    "top" -> Alignment.Top
                    "bottom" -> Alignment.Bottom
                    else -> Alignment.CenterVertically
                },
            ) {
                for (child in ui.children) {
                    RikkaUiRenderer(child, renderId, onSubmit, onNavigate)
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
            Button(onClick = { submit(ui.action) }) {
                Text(ui.label)
            }
        }

        is RikkaUi.Chip -> {
            AssistChip(
                onClick = ui.action?.let { { submit(it) } } ?: {},
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

        is RikkaUi.Input -> {
            OutlinedTextField(
                value = values[ui.key] ?: "",
                onValueChange = { new -> values = values + (ui.key to new) },
                placeholder = ui.placeholder?.let { { Text(it) } },
                label = ui.label?.let { { Text(it) } },
                singleLine = true,
                modifier = modifier.fillMaxWidth(),
            )
        }

        is RikkaUi.Toggle -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = values[ui.key] == "true",
                    onCheckedChange = { checked -> values = values + (ui.key to checked.toString()) },
                )
                Text(ui.label, style = MaterialTheme.typography.bodyMedium)
            }
        }

        is RikkaUi.Select -> {
            SelectDropdown(
                key = ui.key,
                label = ui.label,
                options = ui.options,
                selected = values[ui.key],
                onSelect = { option -> values = values + (ui.key to option) },
            )
        }

        is RikkaUi.Progress -> {
            LinearProgressIndicator(
                progress = { ui.fraction ?: 0f },
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }

        is RikkaUi.Link -> {
            TextButton(onClick = { submit(RikkaUiAction.OpenUrl(ui.url)) }) {
                Text(ui.label)
            }
        }
    }
}

@Composable
private fun SelectDropdown(
    key: String,
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun handleAction(
    action: RikkaUiAction,
    renderId: String,
    values: Map<String, String>,
    onSubmit: (RikkaUiEvent.FormSubmit) -> Unit,
    onNavigate: (String) -> Unit,
    clipboard: ClipboardManager,
    context: Context,
) {
    when (action) {
        is RikkaUiAction.Copy -> {
            clipboard.setText(AnnotatedString(action.text))
        }

        is RikkaUiAction.OpenUrl -> {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)))
        }

        is RikkaUiAction.Submit -> {
            onSubmit(RikkaUiEvent.FormSubmit(renderId, action.formId, values))
        }

        is RikkaUiAction.Navigate -> {
            onNavigate(action.destination)
        }
    }
}
