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
import me.rerere.rikkahub.ui.pages.models.ModelCapabilityRow

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
                            if (!model.providerEnabled) {
                                Text(
                                    stringResource(R.string.models_source_disabled),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
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
