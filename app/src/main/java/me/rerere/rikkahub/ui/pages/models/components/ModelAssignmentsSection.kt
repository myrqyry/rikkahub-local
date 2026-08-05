package me.rerere.rikkahub.ui.pages.models.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.modelregistry.ModelAssignments
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelRole
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.data.modelregistry.capability
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.pages.models.LegacyAssignments
import me.rerere.rikkahub.ui.pages.models.RepairState

internal fun compatibleAssignments(
    role: ModelRole,
    models: List<ModelDescriptor>,
): List<ModelDescriptor> = models.filter {
    it.providerEnabled && it.supports(role.capability()) &&
        (it.source !is ModelSource.Local || it.lifecycle == ModelLifecycle.READY)
}

@Composable
fun ModelAssignmentsSection(
    assignments: ModelAssignments,
    legacyAssignments: LegacyAssignments,
    models: List<ModelDescriptor>,
    repairState: RepairState?,
    onAssign: (ModelRole, String?) -> Unit,
    onAssignTitle: (String?) -> Unit,
    onAssignTranslation: (String?) -> Unit,
    fastModelId: String?,
    suggestionModelId: String?,
    compressModelId: String?,
    enableSuggestion: Boolean,
    onSuggestionEnabledChange: (Boolean) -> Unit,
    onFastModelSelected: (String) -> Unit,
    onSuggestionModelSelected: (String?) -> Unit,
    onCompressModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowStateHolder = rememberAssignmentRowState()
    val rows = listOf(
        AssignmentRow("Chat", ModelRole.CHAT, assignments.defaults[ModelRole.CHAT], false, { onAssign(ModelRole.CHAT, it) }, "Conversation"),
        AssignmentRow("Vision", ModelRole.VISION, assignments.defaults[ModelRole.VISION], false, { onAssign(ModelRole.VISION, it) }),
        AssignmentRow("OCR", ModelRole.OCR, assignments.defaults[ModelRole.OCR], false, { onAssign(ModelRole.OCR, it) }),
        AssignmentRow("Image generation", ModelRole.IMAGE_GENERATION, assignments.defaults[ModelRole.IMAGE_GENERATION], false, { onAssign(ModelRole.IMAGE_GENERATION, it) }, "Media"),
        AssignmentRow("Embeddings", ModelRole.EMBEDDINGS, assignments.defaults[ModelRole.EMBEDDINGS], false, { onAssign(ModelRole.EMBEDDINGS, it) }, "Knowledge"),
        AssignmentRow("Title generation", null, legacyAssignments.titleModelId, true, onAssignTitle, "Utility models"),
        AssignmentRow("Translation", null, legacyAssignments.translationModelId, false, onAssignTranslation),
        AssignmentRow("Fast model", null, fastModelId, false, { id -> id?.let(onFastModelSelected) }),
        AssignmentRow("Compression model", null, compressModelId, false, { id -> id?.let(onCompressModelSelected) }),
    )

    CardGroup(
        modifier = modifier,
        title = { Text("Model assignments") },
    ) {
        item(
            headlineContent = { Text("Enable suggestions") },
            trailingContent = {
                Switch(
                    checked = enableSuggestion,
                    onCheckedChange = onSuggestionEnabledChange,
                )
            },
        )
        rows.forEach { row ->
            val candidates = compatibleAssignments(row.role ?: ModelRole.CHAT, models)
            val selected = candidates.firstOrNull { it.id == row.modelId }
            val unavailable = row.modelId != null && (
                selected == null || repairStateMatches(repairState, row)
            )
            item(
                onClick = { rowStateHolder.open(row, candidates) },
                overlineContent = row.overline?.let { { Text(it) } },
                headlineContent = { Text(row.label) },
                supportingContent = if (unavailable) {
                    { Text("Unavailable: ${row.modelId}", color = MaterialTheme.colorScheme.error) }
                } else null,
                trailingContent = {
                    AssignmentValue(
                        model = selected,
                        allowClear = row.allowClear,
                        onClear = { row.onSelected(null) },
                        onOpen = { rowStateHolder.open(row, candidates) },
                    )
                },
            )
        }
        if (enableSuggestion) {
            val row = AssignmentRow("Suggestion model", null, suggestionModelId, true, onSuggestionModelSelected)
            val candidates = compatibleAssignments(ModelRole.CHAT, models)
            val selected = candidates.firstOrNull { it.id == row.modelId }
            item(
                onClick = { rowStateHolder.open(row, candidates) },
                headlineContent = { Text(row.label) },
                supportingContent = if (row.modelId != null && selected == null) {
                    { Text("Unavailable: ${row.modelId}", color = MaterialTheme.colorScheme.error) }
                } else null,
                trailingContent = {
                    AssignmentValue(
                        model = selected,
                        allowClear = true,
                        onClear = { row.onSelected(null) },
                        onOpen = { rowStateHolder.open(row, candidates) },
                    )
                },
            )
        }
    }

    rowStateHolder.sheet?.let { (row, candidates) ->
        DescriptorSelectorSheet(
            selectedId = row.modelId,
            models = candidates,
            allowClear = row.allowClear,
            onDismiss = rowStateHolder::close,
            onSelect = { row.onSelected(it) },
        )
    }
}

private data class AssignmentRow(
    val label: String,
    val role: ModelRole?,
    val modelId: String?,
    val allowClear: Boolean,
    val onSelected: (String?) -> Unit,
    val overline: String? = null,
)

private class AssignmentRowState {
    var sheet by mutableStateOf<Pair<AssignmentRow, List<ModelDescriptor>>?>(null)
        private set

    fun open(row: AssignmentRow, candidates: List<ModelDescriptor>) { sheet = row to candidates }
    fun close() { sheet = null }
}

@Composable
private fun rememberAssignmentRowState(): AssignmentRowState = remember { AssignmentRowState() }

@Composable
private fun AssignmentValue(
    model: ModelDescriptor?,
    allowClear: Boolean,
    onClear: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onOpen) {
            Text(
                text = model?.displayName ?: stringResource(R.string.model_list_select_model),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (allowClear && model != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                Icon(HugeIcons.Cancel01, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        } else {
            Icon(HugeIcons.ArrowRight01, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun DescriptorSelectorSheet(
    selectedId: String?,
    models: List<ModelDescriptor>,
    allowClear: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val filtered = remember(models, query) {
        models.filter { it.displayName.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.model_list_search_placeholder)) },
                leadingIcon = { Icon(HugeIcons.Search01, contentDescription = null) },
                colors = TextFieldDefaults.colors(
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                ),
                maxLines = 1,
            )
            if (allowClear && selectedId != null) {
                ListItem(
                    headlineContent = { Text("Clear assignment") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(null)
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                    leadingContent = { Icon(HugeIcons.Cancel01, contentDescription = null) },
                    trailingContent = { Text("Current") },
                )
            }
            LazyColumn {
                descriptorGroups(filtered).forEach { group ->
                    item(key = "header:${group.label}") {
                        Text(
                            text = group.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                    items(group.models, key = { it.id }) { model ->
                        ListItem(
                            headlineContent = { Text(model.displayName) },
                            supportingContent = { Text(group.label) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(model.id)
                                    scope.launch {
                                        sheetState.hide()
                                        onDismiss()
                                    }
                                },
                            trailingContent = { if (model.id == selectedId) Text("Selected") },
                        )
                    }
                }
            }
        }
    }
}

internal data class DescriptorGroup(val label: String, val models: List<ModelDescriptor>)

internal fun descriptorGroups(models: List<ModelDescriptor>): List<DescriptorGroup> {
    val cloud = models.filter { it.source is ModelSource.Cloud }
        .groupBy { model ->
            val source = model.source as ModelSource.Cloud
            model.metadata["provider"] ?: source.providerId
        }
        .toSortedMap()
        .map { (label, grouped) -> DescriptorGroup(label, grouped) }
    val local = models.filter { it.source is ModelSource.Local }
    return cloud + if (local.isEmpty()) emptyList() else listOf(DescriptorGroup("Local", local))
}

private fun repairStateMatches(state: RepairState?, row: AssignmentRow): Boolean = when (state) {
    is RepairState.ModelUnavailable -> state.role == row.role && state.modelId == row.modelId
    is RepairState.LegacyModelUnavailable -> when (state.key) {
        me.rerere.rikkahub.ui.pages.models.LegacyAssignmentKey.TITLE -> row.label == "Title generation"
        me.rerere.rikkahub.ui.pages.models.LegacyAssignmentKey.TRANSLATION -> row.label == "Translation"
    } && state.modelId == row.modelId
    null -> false
}
