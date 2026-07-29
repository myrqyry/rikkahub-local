package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.rag.VectorDao
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject

@Composable
fun SettingRAGPage() {
    val settingsStore: SettingsStore = koinInject()
    val settings = LocalSettings.current
    val vectorDao: VectorDao = koinInject()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val documentCount by produceState(initialValue = -1) {
        value = vectorDao.count()
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("RAG Settings") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("ragToggle") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("RAG") },
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.Database02, null) },
                        headlineContent = { Text("Enable RAG") },
                        supportingContent = { Text("Use embeddings for context-aware retrieval") },
                        trailingContent = {
                            Switch(
                                checked = settings.enableRag,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        settingsStore.update { it.copy(enableRag = enabled) }
                                    }
                                }
                            )
                        },
                    )
                }
            }

            item("embeddingModel") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("Embedding Model") },
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.Database02, null) },
                        headlineContent = { Text("Model") },
                        supportingContent = { Text(settings.ragEmbeddingModel) },
                    )
                }
            }

            item("vectorStore") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("Vector Store") },
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.Database02, null) },
                        headlineContent = { Text("Indexed Documents") },
                        supportingContent = {
                            Text(
                                if (documentCount < 0) "Loading..."
                                else "$documentCount documents indexed"
                            )
                        },
                    )
                    item(
                        leadingContent = {
                            Icon(HugeIcons.Tick01, null)
                        },
                        headlineContent = { Text("Last Indexed") },
                        supportingContent = { Text("Open ingestion screen to index documents") },
                    )
                }
            }
        }
    }
}