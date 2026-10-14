package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.opencode.OpenCodeMessage
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Mobile translation of Meristem OpenCode's quiet canvas / raised work surface /
 * accent-outline hierarchy. The visual state must not imply remote execution
 * finished, a provider is healthy, or an agent is working unless observed.
 */
@Composable
fun OpenCodeWorkspaceDetailPage(refId: String) {
    val vm: OpenCodeWorkspaceDetailVM = koinViewModel(parameters = { parametersOf(refId) })
    val state by vm.state.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workspace_opencode_detail_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    TextButton(onClick = vm::refresh, enabled = state.session != null && !state.loading && !state.sending) {
                        Text(stringResource(R.string.workspace_opencode_detail_refresh))
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = scheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.error?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    color = scheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(it, color = scheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                }
            }
            val session = state.session
            if (session == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    color = scheme.surfaceContainerLow,
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.workspace_opencode_detail_no_session),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Button(onClick = vm::createSession, enabled = !state.loading) {
                            if (state.loading) {
                                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                            } else {
                                Text(stringResource(R.string.workspace_opencode_detail_start_session))
                            }
                        }
                    }
                }
            } else {
                // Session identity only: this strip does not claim liveness or remote health.
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    color = scheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.65f)),
                    tonalElevation = 1.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.width(3.dp).height(24.dp)
                                .background(scheme.primary, RoundedCornerShape(2.dp))
                        )
                        Text("OpenCode", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                        Text(
                            session.id.take(12),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        OpenCodeMessageSurface(message)
                    }
                }
                if (state.loading || state.sending) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Surface(
                    color = scheme.surfaceContainerLow,
                    tonalElevation = 2.dp,
                    shadowElevation = 3.dp,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = prompt,
                                onValueChange = { prompt = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.workspace_opencode_detail_message_hint)) },
                                shape = RoundedCornerShape(16.dp),
                                maxLines = 5,
                                enabled = !state.sending,
                            )
                            Button(
                                onClick = {
                                    vm.send(prompt)
                                    prompt = ""
                                },
                                enabled = prompt.isNotBlank() && !state.sending,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(stringResource(R.string.workspace_opencode_detail_send))
                            }
                        }
                        if (state.sending) {
                            TextButton(onClick = vm::abort) {
                                Text(stringResource(R.string.workspace_opencode_detail_abort))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenCodeMessageSurface(message: OpenCodeMessage) {
    val scheme = MaterialTheme.colorScheme
    val isUser = message.role.equals("user", ignoreCase = true)
    val accent = if (isUser) scheme.primary else scheme.secondary
    val surface = if (isUser) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainerLow

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.93f else 1f),
            color = surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.20f)),
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.width(3.dp).height(30.dp)
                        .background(accent.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                )
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        message.role,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                    Text(message.text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                }
            }
        }
    }
}
