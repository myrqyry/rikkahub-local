package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.locallm.AcceleratorProbe
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.LocalRuntimePreferences
import me.rerere.locallm.litert.LiteRtBenchmarkSummary
import me.rerere.locallm.litert.LiteRtRuntime
import me.rerere.locallm.litert.canBenchmarkGpu
import me.rerere.locallm.litert.isGpuDelegateCompilationFailure
import me.rerere.rikkahub.R
import org.koin.compose.koinInject

/**
 * User-triggered, measured LiteRT-LM performance tests. Never runs during ordinary chat
 * and never silently switches the requested accelerator. Reuses the shared runtime gate.
 */
@Composable
internal fun LiteRtBenchmarkPanel() {
    val context = LocalContext.current
    val prefs = remember(context) { LocalRuntimePreferences(context.applicationContext) }
    val models by prefs.installedModelsFlow(LocalRuntime.LiteRT)
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val forceCpu by prefs.forceCpuFlow(LocalRuntime.LiteRT)
        .collectAsStateWithLifecycle(initialValue = true)
    val gpuDelegateSupported = remember(context) {
        AcceleratorProbe.probeLiteRtCapabilities(context).gpuDelegateSupported
    }
    val gpuAllowed = canBenchmarkGpu(forceCpu, gpuDelegateSupported)
    val runtime: LiteRtRuntime = koinInject()
    val scope = rememberCoroutineScope()

    var accelerator by remember { mutableStateOf("cpu") }
    val effectiveAccelerator = if (gpuAllowed) accelerator else "cpu"
    var runningModel by remember { mutableStateOf<String?>(null) }
    var completedRuns by remember { mutableStateOf(0) }
    var resultModel by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<LiteRtBenchmarkSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val candidates = models.toSortedMap().filter { (name, path) ->
        name.endsWith(".litertlm", ignoreCase = true) && File(path).isFile
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.local_llm_benchmark_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(R.string.local_llm_benchmark_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("cpu", "gpu").forEachIndexed { index, value ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    selected = effectiveAccelerator == value,
                    enabled = runningModel == null && (value == "cpu" || gpuAllowed),
                    onClick = { if (value == "cpu" || gpuAllowed) accelerator = value },
                    label = { Text(value.uppercase(Locale.ROOT)) },
                )
            }
        }
        if (!gpuAllowed) {
            Text(
                stringResource(
                    if (forceCpu) R.string.local_llm_benchmark_gpu_opt_in
                    else R.string.local_llm_benchmark_gpu_unsupported,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (candidates.isEmpty()) {
            Text(
                stringResource(R.string.local_llm_benchmark_no_models),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        candidates.forEach { (modelName, path) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    modelName,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    enabled = runningModel == null,
                    onClick = {
                        scope.launch {
                            runningModel = modelName
                            completedRuns = 0
                            resultModel = modelName
                            result = null
                            error = null
                            try {
                                result = runtime.runNativeBenchmark(
                                    modelPath = path,
                                    accelerator = effectiveAccelerator,
                                ) { done ->
                                    withContext(Dispatchers.Main.immediate) {
                                        completedRuns = done
                                    }
                                }
                            } catch (e: Exception) {
                                error = if (isGpuDelegateCompilationFailure(effectiveAccelerator, e.message)) {
                                    context.getString(R.string.local_llm_benchmark_gpu_delegate_failed)
                                } else {
                                    e.message?.lineSequence()?.firstOrNull()?.take(220)
                                        ?: e.javaClass.simpleName
                                }
                            } finally {
                                runningModel = null
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.local_llm_benchmark_run))
                }
            }
        }

        runningModel?.let { active ->
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.local_llm_benchmark_running, active, completedRuns),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (resultModel != null && runningModel == null) {
            result?.let { measured ->
                HorizontalDivider()
                Text(
                    resultModel ?: "",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    stringResource(
                        R.string.local_llm_benchmark_result,
                        measured.runs,
                        benchmarkMetric(measured.firstRunDurationMs, " ms"),
                        benchmarkMetric(measured.laterRunMedianMs, " ms"),
                        benchmarkMetric(measured.firstTokenMedianMs, " ms"),
                        benchmarkMetric(measured.prefillMedianTokensPerSecond, " tok/s"),
                        benchmarkMetric(measured.decodeMedianTokensPerSecond, " tok/s"),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            error?.let { failure ->
                Text(
                    stringResource(R.string.local_llm_benchmark_failed, failure),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun benchmarkMetric(value: Double?, unit: String): String =
    value?.let { String.format(Locale.US, "%.1f%s", it, unit) } ?: "n/a"
