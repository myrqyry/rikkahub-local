package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
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
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.rag.VectorDao
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
    val settings = LocalSettings.current
    val vectorDao: VectorDao = koinInject()
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

    val documentCount by produceState(initialValue = -1) {
        value = vectorDao.count()
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
                                if (documentCount < 0) stringResource(R.string.setting_rag_loading)
                                else stringResource(R.string.setting_rag_documents_indexed, documentCount)
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
