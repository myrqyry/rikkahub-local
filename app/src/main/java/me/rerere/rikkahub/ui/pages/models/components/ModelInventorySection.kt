package me.rerere.rikkahub.ui.pages.models.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.pages.models.ModelInventoryStatus
import me.rerere.rikkahub.ui.pages.models.ModelCapabilityRow
import me.rerere.rikkahub.ui.pages.models.inventoryStatus
import me.rerere.rikkahub.ui.pages.models.labelRes

@Composable
fun ModelInventorySection(
    models: List<ModelDescriptor>,
    onModelEnabledChange: (ModelDescriptor, Boolean) -> Unit,
    onModelClick: (ModelDescriptor) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (models.isEmpty()) {
            Text(
                stringResource(R.string.unified_models_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CardGroup {
                models.forEach { model ->
                    item(
                        onClick = { onModelClick(model) },
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(model.displayName)
                                SourceBadge(
                                    model = model,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        },
                        supportingContent = {
                            ModelCapabilityRow(model.capabilities)
                            model.inventoryStatus()?.let { status ->
                                Text(
                                    stringResource(
                                        when (status) {
                                            ModelInventoryStatus.PROVIDER_DISABLED -> R.string.models_source_disabled
                                            ModelInventoryStatus.CONNECTION_UNAVAILABLE -> R.string.models_connection_unavailable
                                            ModelInventoryStatus.NOT_READY -> model.lifecycle.labelRes
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (
                                        status == ModelInventoryStatus.PROVIDER_DISABLED ||
                                        status == ModelInventoryStatus.CONNECTION_UNAVAILABLE
                                    ) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = model.enabledCapabilities.isNotEmpty(),
                                enabled = model.providerEnabled,
                                onCheckedChange = { onModelEnabledChange(model, it) },
                            )
                        },
                    )
                }
            }
        }
    }
}
