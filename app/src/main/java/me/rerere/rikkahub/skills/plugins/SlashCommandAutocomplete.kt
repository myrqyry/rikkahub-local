package me.rerere.rikkahub.skills.plugins

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * CompositionLocal that provides the active [SlashCommandRegistry] down the
 * composable tree. Must be set by a parent via [CompositionLocalProvider].
 */
val LocalSlashCommandRegistry = staticCompositionLocalOf<SlashCommandRegistry> {
    error("No SlashCommandRegistry provided. Wrap your composable tree with CompositionLocalProvider(LocalSlashCommandRegistry provides registry) { ... }")
}

/**
 * A lightweight autocomplete popup for slash commands.
 *
 * When [text] starts with "/", the component queries the nearest
 * [SlashCommandRegistry] (via [LocalSlashCommandRegistry]) and renders a
 * dropdown of matching commands. Selecting a match calls [onSelect] with the
 * full command name (e.g. "/help").
 *
 * @param text     The current input text to evaluate for autocomplete.
 * @param onSelect Callback invoked with the selected command string.
 * @param modifier Optional [Modifier] applied to the outer container.
 */
@Composable
fun SlashCommandAutocomplete(
    text: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only show autocomplete when the user is typing a slash command
    if (!text.startsWith("/")) return

    val registry = LocalSlashCommandRegistry.current
    val matches = registry.matchPartial(text)

    Column(modifier = modifier) {
        DropdownMenu(
            expanded = matches.isNotEmpty(),
            onDismissRequest = { /* managed by the parent's input focus */ },
        ) {
            matches.forEach { match ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "/${match.command.name}  —  ${match.command.description}",
                        )
                    },
                    onClick = { onSelect("/${match.command.name}") },
                )
            }
        }
    }
}