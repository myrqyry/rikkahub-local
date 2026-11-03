package me.rerere.rikkahub.ui.pages.models.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.icons.MingcuteIcons as HugeIcons
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelProviderDescriptor
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.pages.models.iconRes
import me.rerere.rikkahub.ui.theme.CustomColors

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

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (models.isEmpty()) {
            Text(
                text = stringResource(R.string.unified_models_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 20.dp),
            )
            return@Column
        }

        visibleProviderGroups.forEach { group ->
            val provider = group.provider
            val expanded = provider.id !in collapsedProviderIds
            ModelProviderCard(
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
            ) {
                group.models.forEachIndexed { index, model ->
                    key(model.id) {
                        CompactModelRow(
                            model = model,
                            onClick = { onModelClick(model) },
                            onEnabledChange = { onModelEnabledChange(model, it) },
                        )
                        if (index != group.models.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        }
                    }
                }
            }
        }

        if (ungroupedModels.isNotEmpty()) {
            val localGroupId = "__local_inventory__"
            val expanded = localGroupId !in collapsedProviderIds
            ModelProviderCard(
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
                showEnabledTag = false,
            ) {
                ungroupedModels.forEachIndexed { index, model ->
                    key(model.id) {
                        CompactModelRow(
                            model = model,
                            onClick = { onModelClick(model) },
                            onEnabledChange = { onModelEnabledChange(model, it) },
                        )
                        if (index != ungroupedModels.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        }
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
private fun ModelProviderCard(
    title: String,
    modelCount: Int,
    enabled: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSettings: (() -> Unit)?,
    showEnabledTag: Boolean = true,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.82f),
        label = "provider chevron rotation",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AutoAIIcon(
                name = title,
                modifier = Modifier.size(40.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (showEnabledTag) {
                        Tag(type = if (enabled) TagType.SUCCESS else TagType.WARNING) {
                            Text(
                                stringResource(
                                    if (enabled) R.string.setting_provider_page_enabled
                                    else R.string.setting_provider_page_disabled
                                )
                            )
                        }
                    }
                    Tag(type = TagType.INFO) {
                        Text(stringResource(R.string.unified_models_provider_count, modelCount))
                    }
                }
            }
            if (onSettings != null) {
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = HugeIcons.Settings03,
                        contentDescription = stringResource(R.string.models_provider_settings),
                    )
                }
            }
            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                HorizontalDivider()
                content()
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
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AutoAIIcon(
            name = model.displayName,
            modifier = Modifier.size(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SourceBadge(model = model)
                val capabilities = model.capabilities.sortedBy { it.name }
                capabilities.take(4).forEach { capability ->
                    Icon(
                        painter = painterResource(capability.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (capabilities.size > 4) {
                    Text(
                        text = "+" + (capabilities.size - 4),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Switch(
            checked = model.enabledCapabilities.isNotEmpty(),
            enabled = model.providerEnabled,
            onCheckedChange = onEnabledChange,
        )
    }
}
