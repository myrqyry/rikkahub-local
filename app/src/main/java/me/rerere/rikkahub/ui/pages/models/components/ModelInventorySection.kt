package me.rerere.rikkahub.ui.pages.models.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelProviderDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelSource
import me.rerere.rikkahub.ui.pages.models.icon
import androidx.compose.ui.res.stringResource

@Composable
fun ModelInventorySection(
    models: List<ModelDescriptor>,
    providers: List<ModelProviderDescriptor>,
    onModelEnabledChange: (ModelDescriptor, Boolean) -> Unit,
    onModelClick: (ModelDescriptor) -> Unit = {},
    onProviderClick: (ModelProviderDescriptor) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var collapsedProviderIds by remember { mutableStateOf(emptySet<String>()) }
    val modelsById = models.associateBy { it.id }
    val providerModelIds = providers.flatMapTo(mutableSetOf()) { it.modelIds }
    val visibleProviderGroups = providers.mapNotNull { provider ->
        val providerModels = provider.modelIds.mapNotNull(modelsById::get)
        if (providerModels.isEmpty()) null else ProviderGroup(provider, providerModels)
    }
    val ungroupedModels = models.filter { it.id !in providerModelIds }

    Column(modifier = modifier) {
        if (models.isEmpty()) {
            Text(
                stringResource(R.string.unified_models_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        visibleProviderGroups.forEachIndexed { index, group ->
            val provider = group.provider
            val expanded = provider.id !in collapsedProviderIds
            ProviderHeader(
                title = provider.displayName,
                modelCount = group.models.size,
                enabled = provider.enabled,
                expanded = expanded,
                onToggle = {
                    collapsedProviderIds = if (expanded) {
                        collapsedProviderIds + provider.id
                    } else {
                        collapsedProviderIds - provider.id
                    }
                },
                onSettings = { onProviderClick(provider) },
            )
            if (expanded) {
                group.models.forEachIndexed { modelIndex, model ->
                    CompactModelRow(
                        model = model,
                        onClick = { onModelClick(model) },
                        onEnabledChange = { onModelEnabledChange(model, it) },
                    )
                    if (modelIndex != group.models.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
                    }
                }
            }
            if (index != visibleProviderGroups.lastIndex || ungroupedModels.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        if (ungroupedModels.isNotEmpty()) {
            val localGroupId = "__local_inventory__"
            val expanded = localGroupId !in collapsedProviderIds
            ProviderHeader(
                title = stringResource(R.string.models_on_this_device),
                modelCount = ungroupedModels.size,
                enabled = true,
                expanded = expanded,
                onToggle = {
                    collapsedProviderIds = if (expanded) {
                        collapsedProviderIds + localGroupId
                    } else {
                        collapsedProviderIds - localGroupId
                    }
                },
                onSettings = null,
            )
            if (expanded) {
                ungroupedModels.forEachIndexed { index, model ->
                    CompactModelRow(
                        model = model,
                        onClick = { onModelClick(model) },
                        onEnabledChange = { onModelEnabledChange(model, it) },
                    )
                    if (index != ungroupedModels.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
                    }
                }
            }
        }
    }
}

private data class ProviderGroup(
    val provider: ModelProviderDescriptor,
    val models: List<ModelDescriptor>,
)

@Composable
private fun ProviderHeader(
    title: String,
    modelCount: Int,
    enabled: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSettings: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = HugeIcons.ArrowRight01,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .rotate(if (expanded) 90f else 0f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape,
                ),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.unified_models_provider_count, modelCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onSettings != null) {
            IconButton(
                onClick = onSettings,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Settings03,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CompactModelRow(
    model: ModelDescriptor,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 28.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = model.displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SourceBadge(model = model)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val capabilities = model.capabilities.sortedBy { it.name }
            capabilities.take(3).forEach { capability ->
                Icon(
                    imageVector = capability.icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (capabilities.size > 3) {
                Text(
                    text = "+${capabilities.size - 3}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = when {
                        !model.providerEnabled -> MaterialTheme.colorScheme.outline
                        model.source is ModelSource.Local && model.lifecycle == ModelLifecycle.ERROR -> {
                            MaterialTheme.colorScheme.error
                        }
                        model.enabledCapabilities.isEmpty() -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.primary
                    },
                    shape = CircleShape,
                ),
        )
        Switch(
            checked = model.enabledCapabilities.isNotEmpty(),
            enabled = model.providerEnabled,
            onCheckedChange = onEnabledChange,
        )
    }
}
