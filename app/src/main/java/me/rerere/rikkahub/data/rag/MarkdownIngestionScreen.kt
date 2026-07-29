package me.rerere.rikkahub.data.rag

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting

@Composable
fun MarkdownIngestionScreen(
    pipeline: MarkdownIngestionPipeline,
    providerSetting: ProviderSetting,
    model: Model,
    onBack: () -> Unit,
) {
    var markdownText by remember { mutableStateOf("") }
    var docId by remember { mutableStateOf("") }
    var isIngesting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<IngestionProgress?>(null) }
    val completedChunks = remember { mutableListOf<String>() }
    var completedCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Markdown Ingestion") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = markdownText,
                onValueChange = { markdownText = it },
                label = { Text("Markdown content") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 20,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = docId,
                onValueChange = { docId = it },
                label = { Text("Document ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (markdownText.isNotBlank() && docId.isNotBlank() && !isIngesting) {
                        isIngesting = true
                        completedChunks.clear()
                        completedCount = 0
                        scope.launch {
                            pipeline.ingestFromText(markdownText, docId, providerSetting, model)
                                .collect { p ->
                                    progress = p
                                    if (p.processedChunks > completedCount) {
                                        completedChunks.add("Chunk ${p.processedChunks}: ${p.currentChunk}")
                                        completedCount = p.processedChunks
                                    }
                                }
                            isIngesting = false
                        }
                    }
                },
                enabled = markdownText.isNotBlank() && docId.isNotBlank() && !isIngesting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isIngesting) "Ingesting..." else "Ingest")
            }

            Spacer(Modifier.height(16.dp))

            if (isIngesting || progress != null) {
                val p = progress
                if (p != null && p.totalChunks > 0) {
                    LinearProgressIndicator(
                        progress = { p.processedChunks.toFloat() / p.totalChunks },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${p.processedChunks}/${p.totalChunks} chunks — ${p.currentChunk}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (isIngesting) {
                    Row {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text("Chunking document...")
                    }
                }

                if (p != null && p.errors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Errors: ${p.errors.size}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (completedChunks.isNotEmpty()) {
                Text(
                    text = "Processed chunks:",
                    style = MaterialTheme.typography.titleSmall,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(completedChunks) { chunk ->
                        Text(
                            text = chunk,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}