package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.ui.icons.MingcuteIcons as HugeIcons
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.rag.EmbeddingIndexStatus
import me.rerere.rikkahub.data.rag.EmbeddingRepository
import me.rerere.rikkahub.data.rag.EmbeddingGemma2Variant
import me.rerere.rikkahub.data.rag.MediaRagIngestion
import me.rerere.rikkahub.data.rag.LocalVectorSearchEngine
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.setting.components.QwenSemanticModelManager
import me.rerere.rikkahub.ui.pages.setting.components.QwenSemanticModelSetupCard
import me.rerere.rikkahub.ui.pages.setting.components.QwenSemanticModelSetupViewModel
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SettingRAGPage() {
    val settingsStore: SettingsStore = koinInject()
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = LocalSettings.current
    val embeddingRepository: EmbeddingRepository = koinInject()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val setupVm: QwenSemanticModelSetupViewModel = koinViewModel()
    val embedderStatus by setupVm.embedderStatus.collectAsStateWithLifecycle()
    val rerankerStatus by setupVm.rerankerStatus.collectAsStateWithLifecycle()
    val activeOperation by setupVm.activeOperation.collectAsStateWithLifecycle()
    val setupError by setupVm.errorMessage.collectAsStateWithLifecycle()
    var pendingFolderKind by remember { mutableStateOf<QwenSemanticModelManager.ModelKind?>(null) }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val kind = pendingFolderKind
        pendingFolderKind = null
        if (uri != null && kind != null) setupVm.chooseFolder(kind, uri)
    }

    LaunchedEffect(settings) {
        setupVm.refresh(settings)
    }

    val selectedEmbeddingVariant = EmbeddingGemma2Variant.forModelId(settings.ragEmbeddingModel)
    var mediaImportBusy by remember { mutableStateOf(false) }
    var mediaImportStatus by remember { mutableStateOf<String?>(null) }
    var mediaSearchQuery by remember { mutableStateOf("") }
    var mediaSearchBusy by remember { mutableStateOf(false) }
    var mediaSearchResults by remember { mutableStateOf<List<LocalVectorSearchEngine.ScoredMatch>>(emptyList()) }
    var indexRefresh by remember { mutableStateOf(0) }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val variant = selectedEmbeddingVariant
        if (uri != null && variant != null) {
            scope.launch {
                mediaImportBusy = true
                mediaImportStatus = "Embedding media locally..."
                try {
                    val result = MediaRagIngestion(context, embeddingRepository).ingest(uri, variant)
                    mediaImportStatus = "Indexed ${result.indexed}/${result.total} ${result.format.name.lowercase()} item(s) from ${result.name}."
                    indexRefresh++
                    mediaSearchResults = emptyList()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mediaImportStatus = "Media embedding failed: ${e.message ?: "unknown error"}"
                } finally {
                    mediaImportBusy = false
                }
            }
        }
    }
    val indexStatus by produceState(
        initialValue = EmbeddingIndexStatus(0, stale = false),
        settings.ragEmbeddingModel, indexRefresh,
    ) {
        value = embeddingRepository.indexStatus()
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_rag_page_title)) },
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
                    title = { Text(stringResource(R.string.setting_rag_group_rag)) },
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.Database02, null) },
                        headlineContent = { Text(stringResource(R.string.setting_rag_enable)) },
                        supportingContent = { Text(stringResource(R.string.setting_rag_enable_desc)) },
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
                QwenSemanticModelSetupCard(
                    embedderStatus = embedderStatus,
                    rerankerStatus = rerankerStatus,
                    activeOperation = activeOperation,
                    errorMessage = setupError,
                    onDownload = setupVm::download,
                    onChooseFolder = { kind ->
                        pendingFolderKind = kind
                        folderPicker.launch(null)
                    },
                    onDismissError = setupVm::clearError,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            item("multimodalMedia") {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Local multimodal retrieval", style = MaterialTheme.typography.titleMedium)
                    if (selectedEmbeddingVariant?.supportsImage == true) {
                        Text(
                            "Index JPEG, PNG, visual PDF pages, or MP4 video frames with EmbeddingGemma 2 " +
                                "${selectedEmbeddingVariant.name}. The 740M bundle also embeds WAV audio. " +
                                "Original files remain on your device; vectors and source metadata are stored locally.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = {
                                val types = mutableListOf(
                                    "image/png", "image/jpeg", "application/pdf", "video/mp4",
                                )
                                if (selectedEmbeddingVariant.supportsAudio) {
                                    types += listOf("audio/wav", "audio/x-wav")
                                }
                                mediaPicker.launch(types.toTypedArray())
                            },
                            enabled = !mediaImportBusy && !mediaSearchBusy,
                        ) { Text(if (mediaImportBusy) "Embedding..." else "Index image, video, PDF, or WAV") }
                        OutlinedTextField(
                            value = mediaSearchQuery,
                            onValueChange = { mediaSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search indexed media") },
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    mediaSearchBusy = true
                                    try {
                                        mediaSearchResults = embeddingRepository.searchMedia(mediaSearchQuery)
                                        mediaImportStatus = if (mediaSearchResults.isEmpty()) {
                                            "No matching indexed media in this model's vector space."
                                        } else null
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        mediaImportStatus = "Local search failed: ${e.message}"
                                    } finally {
                                        mediaSearchBusy = false
                                    }
                                }
                            },
                            enabled = mediaSearchQuery.isNotBlank() && !mediaImportBusy && !mediaSearchBusy,
                        ) { Text(if (mediaSearchBusy) "Searching..." else "Search media locally") }
                        mediaSearchResults.forEach { match ->
                            val name = match.metadata["text"]?.jsonPrimitive?.contentOrNull ?: match.id
                            Text("${name} · similarity ${"%.2f".format(match.score)}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(
                            "Select and verify the 440M text+vision or 740M full EmbeddingGemma 2 " +
                                "bundle in the LiteRT model catalog to use native visual/audio retrieval.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    mediaImportStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            item("vectorStore") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_rag_group_vector_store)) },
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.Database02, null) },
                        headlineContent = { Text(stringResource(R.string.setting_rag_indexed_documents)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.setting_rag_documents_indexed,
                                    indexStatus.documentCount,
                                )
                            )
                        },
                    )
                    item(
                        leadingContent = { Icon(HugeIcons.Tick01, null) },
                        headlineContent = {
                            Text(stringResource(R.string.setting_rag_index_status))
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (indexStatus.stale) {
                                        R.string.setting_rag_index_needs_rebuilding
                                    } else {
                                        R.string.setting_rag_index_status_ready
                                    }
                                )
                            )
                        },
                    )
                    item(
                        leadingContent = {
                            Icon(HugeIcons.Tick01, null)
                        },
                        headlineContent = { Text(stringResource(R.string.setting_rag_last_indexed)) },
                        supportingContent = { Text(stringResource(R.string.setting_rag_open_ingestion)) },
                    )
                }
            }
        }
    }
}
