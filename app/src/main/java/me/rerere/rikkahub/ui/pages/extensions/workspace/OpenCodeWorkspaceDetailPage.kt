package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun OpenCodeWorkspaceDetailPage(refId: String) {
    val navController = LocalNavController.current
    val vm: OpenCodeWorkspaceDetailVM = koinViewModel(parameters = { parametersOf(refId) })
    val state by vm.state.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenCode workspace") },
                navigationIcon = { BackButton() },
                actions = {
                    TextButton(onClick = vm::refresh, enabled = state.session != null && !state.loading) { Text("Refresh") }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (state.session == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("No remote session is open.", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = vm::createSession, enabled = !state.loading) {
                        if (state.loading) CircularProgressIndicator(modifier = Modifier.padding(4.dp)) else Text("Start session")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(message.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(message.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (state.loading || state.sending) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message OpenCode") },
                        enabled = !state.sending,
                    )
                    Button(
                        onClick = { vm.send(prompt); prompt = "" },
                        enabled = prompt.isNotBlank() && !state.sending,
                    ) { Text("Send") }
                }
                TextButton(onClick = vm::abort, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Abort session") }
            }
        }
    }
}
