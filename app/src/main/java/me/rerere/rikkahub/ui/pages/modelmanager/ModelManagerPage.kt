package me.rerere.rikkahub.ui.pages.modelmanager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerPage(
    viewModel: ModelManagerViewModel = remember { ModelManagerViewModel() },
) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Installed", "Catalog", "HF URL", "Local Import")

    Scaffold(
        topBar = { TopAppBar(title = { Text("Model Manager") }) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label) },
                    )
                }
            }
            when (tab) {
                0 -> InstalledTab(viewModel)
                1 -> CatalogTab(viewModel)
                2 -> HfUrlTab()
                3 -> LocalImportTab()
            }
        }
    }
}

@Composable
private fun InstalledTab(viewModel: ModelManagerViewModel) {
    // TODO: full implementation in follow-up task
    Text("Installed models")
}

@Composable
private fun CatalogTab(viewModel: ModelManagerViewModel) {
    // TODO: full implementation in follow-up task
    Text("Catalog")
}

@Composable
private fun HfUrlTab() {
    // TODO: full implementation in follow-up task
    Text("Hugging Face URL")
}

@Composable
private fun LocalImportTab() {
    // TODO: full implementation in follow-up task
    Text("Local import")
}
