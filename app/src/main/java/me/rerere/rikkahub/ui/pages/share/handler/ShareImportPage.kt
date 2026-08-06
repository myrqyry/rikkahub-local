package me.rerere.rikkahub.ui.pages.share.handler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.share.ArtifactImportRecognizer
import me.rerere.rikkahub.data.share.SharedPayloadStore
import me.rerere.rikkahub.data.share.ShareRoutingDecision
import me.rerere.rikkahub.skills.imports.ImportCoordinator
import me.rerere.rikkahub.skills.imports.ImportResult
import me.rerere.rikkahub.skills.imports.import
import org.koin.compose.koinInject

@Composable
fun ShareImportPage(handoffId: String) {
    val payloadStore = koinInject<SharedPayloadStore>()
    val importCoordinator = koinInject<ImportCoordinator>()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var installing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val catalogTitle = stringResource(R.string.skill_catalog_title)
    val installLabel = stringResource(R.string.skill_catalog_install)
    val installedLabel = stringResource(R.string.skill_catalog_installed)
    val installFailed = stringResource(R.string.skill_catalog_install_failed)

    LaunchedEffect(handoffId) {
        val handoff = payloadStore.get(handoffId)
        val decision = handoff?.payload?.let { ArtifactImportRecognizer.recognize(it) }
        if (decision is ShareRoutingDecision.ImportCandidate) {
            name = decision.request.source.substringAfterLast('/').substringBefore('?')
        } else {
            message = String.format(installFailed, "unsupported")
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            loading -> CircularProgressIndicator()
            message != null -> Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium
            )
            name != null -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = name.orEmpty(),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = catalogTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    enabled = !installing,
                    onClick = {
                        installing = true
                        scope.launch {
                            val handoff = payloadStore.get(handoffId)
                            val decision = handoff?.payload?.let { ArtifactImportRecognizer.recognize(it) }
                            val result: String = when (val d = decision) {
                                is ShareRoutingDecision.ImportCandidate -> {
                                    val importResult = importCoordinator.import(d.request)
                                    when (importResult) {
                                        is ImportResult.Installed -> installedLabel
                                        is ImportResult.Blocked -> String.format(
                                            installFailed,
                                            importResult.reason
                                        )
                                        is ImportResult.Failed -> String.format(
                                            installFailed,
                                            importResult.code
                                        )
                                    }
                                }
                                is ShareRoutingDecision.ComposerDraft -> String.format(
                                    installFailed,
                                    "unsupported"
                                )
                                is ShareRoutingDecision.Unsupported -> String.format(
                                    installFailed,
                                    d.reason
                                )
                                null -> String.format(
                                    installFailed,
                                    "expired"
                                )
                            }
                            installing = false
                            message = result
                        }
                    }
                ) {
                    if (installing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .align(Alignment.CenterVertically)
                        )
                    }
                    Text(installLabel)                }
            }
        }
    }
}
