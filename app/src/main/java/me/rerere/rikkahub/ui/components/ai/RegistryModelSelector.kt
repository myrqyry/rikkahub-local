package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.isSelectableFor

@Composable
fun RegistryModelSelector(
    value: String?,
    models: List<ModelDescriptor>,
    capability: ModelCapability,
    label: String,
    onSelect: (ModelDescriptor) -> Unit,
    onManage: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = models.firstOrNull { it.id == value }

    TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(selected?.displayName ?: "$label: none")
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            LazyColumn(modifier = Modifier.padding(bottom = 24.dp)) {
                item {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(models.filter { it.isSelectableFor(capability) }) { model ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (capability in model.unverifiedCapabilities) "Unverified capability" else "Ready",
                            modifier = Modifier.padding(16.dp),
                        )
                        TextButton(onClick = {
                            onSelect(model)
                            open = false
                        }) {
                            Text("Select")
                        }
                    }
                }
                item {
                    TextButton(onClick = {
                        onManage()
                        open = false
                    }) {
                        Text("Manage models")
                    }
                }
            }
        }
    }
}
