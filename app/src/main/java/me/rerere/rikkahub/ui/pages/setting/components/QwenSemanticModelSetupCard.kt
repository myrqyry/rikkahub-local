package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Download02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.CustomColors

@Composable
fun QwenSemanticModelSetupCard(
    embedderStatus: QwenSemanticModelManager.ModelStatus,
    rerankerStatus: QwenSemanticModelManager.ModelStatus,
    activeOperation: QwenSetupOperation?,
    errorMessage: String?,
    onDownload: (QwenSemanticModelManager.ModelKind) -> Unit,
    onChooseFolder: (QwenSemanticModelManager.ModelKind) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.setting_search_qwen_setup_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.setting_search_qwen_setup_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            QwenModelStatusRow(
                kind = QwenSemanticModelManager.ModelKind.Embedder,
                status = embedderStatus,
                operation = activeOperation,
                label = stringResource(R.string.setting_search_qwen_embedder),
                description = stringResource(R.string.setting_search_qwen_embedder_desc),
                onDownload = onDownload,
                onChooseFolder = onChooseFolder,
            )
            QwenModelStatusRow(
                kind = QwenSemanticModelManager.ModelKind.Reranker,
                status = rerankerStatus,
                operation = activeOperation,
                label = stringResource(R.string.setting_search_qwen_reranker),
                description = stringResource(R.string.setting_search_qwen_reranker_desc),
                onDownload = onDownload,
                onChooseFolder = onChooseFolder,
            )

            if (errorMessage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = HugeIcons.Alert01,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = errorMessage,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = onDismissError) {
                        Text(stringResource(R.string.setting_search_qwen_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun QwenModelStatusRow(
    kind: QwenSemanticModelManager.ModelKind,
    status: QwenSemanticModelManager.ModelStatus,
    operation: QwenSetupOperation?,
    label: String,
    description: String,
    onDownload: (QwenSemanticModelManager.ModelKind) -> Unit,
    onChooseFolder: (QwenSemanticModelManager.ModelKind) -> Unit,
) {
    val currentOperation = operation?.takeIf { it.kind == kind }
    val statusLabel = when (status) {
        QwenSemanticModelManager.ModelStatus.NotInstalled ->
            stringResource(R.string.setting_search_qwen_status_not_installed)
        is QwenSemanticModelManager.ModelStatus.Incomplete ->
            stringResource(R.string.setting_search_qwen_status_incomplete)
        is QwenSemanticModelManager.ModelStatus.Ready ->
            stringResource(R.string.setting_search_qwen_status_ready)
    }
    val statusIcon = when (status) {
        QwenSemanticModelManager.ModelStatus.NotInstalled -> HugeIcons.Alert01
        is QwenSemanticModelManager.ModelStatus.Incomplete -> HugeIcons.Alert01
        is QwenSemanticModelManager.ModelStatus.Ready -> HugeIcons.CheckmarkCircle02
    }
    val statusColor = when (status) {
        QwenSemanticModelManager.ModelStatus.NotInstalled -> MaterialTheme.colorScheme.onSurfaceVariant
        is QwenSemanticModelManager.ModelStatus.Incomplete -> MaterialTheme.colorScheme.error
        is QwenSemanticModelManager.ModelStatus.Ready -> MaterialTheme.colorScheme.primary
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = statusLabel,
                tint = statusColor,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                )
                if (status is QwenSemanticModelManager.ModelStatus.Incomplete) {
                    Text(
                        stringResource(
                            R.string.setting_search_qwen_missing_files,
                            status.missingFiles.joinToString(", "),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (currentOperation != null) {
            LinearProgressIndicator(
                progress = { currentOperation.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(
                    R.string.setting_search_qwen_download_progress,
                    currentOperation.percent,
                    currentOperation.completedFiles,
                    currentOperation.totalFiles,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (status !is QwenSemanticModelManager.ModelStatus.Ready) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDownload(kind) }) {
                    Icon(HugeIcons.Download02, contentDescription = null)
                    Text(
                        text = stringResource(R.string.setting_search_qwen_download),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(onClick = { onChooseFolder(kind) }) {
                    Icon(HugeIcons.Folder01, contentDescription = null)
                    Text(
                        text = stringResource(R.string.setting_search_qwen_choose_folder),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
