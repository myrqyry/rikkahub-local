package me.rerere.rikkahub.ui.bubble

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.tools.local.AccessibilityServiceHandle
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.RikkaAccessibilityService
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.ui.theme.WaveShape
import org.koin.android.ext.android.inject
import java.util.Locale

class TranslateBubbleActivity : ComponentActivity() {
    private val settingsStore: SettingsStore by inject()
    private val generationHandler: GenerationHandler by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RikkahubTheme {
                TranslateBubbleScreen(
                    onTranslate = { source, target ->
                        val settings = settingsStore.settingsFlow.first()
                        generationHandler.translateText(
                            settings = settings,
                            sourceText = source,
                            targetLanguage = target,
                        )
                    },
                    onDismiss = {
                        TranslateBubble.dismiss(this)
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun TranslateBubbleScreen(
    onTranslate: suspend (String, Locale) -> Flow<String>,
    onDismiss: () -> Unit,
) {
    var source by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(Locale.SIMPLIFIED_CHINESE) }
    var result by remember { mutableStateOf("") }
    var translating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var languageMenuOpen by remember { mutableStateOf(false) }
    var screenStatus by remember { mutableStateOf<String?>(null) }
    var live by remember { mutableStateOf(false) }
    var liveJob by remember { mutableStateOf<Job?>(null) }
    var a11yRunning by remember { mutableStateOf(AccessibilityServiceHandle.isRunning()) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val genericErrorText = stringResource(R.string.translator_error_generic)
    val screenEmptyText = stringResource(R.string.translate_bubble_screen_empty)
    val a11yRequiredText = stringResource(R.string.translate_bubble_a11y_required)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yRunning = AccessibilityServiceHandle.isRunning()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun doTranslate() {
        if (source.isBlank() || translating) return
        translating = true
        error = null
        result = ""
        scope.launch {
            try {
                onTranslate(source, target).collect { chunk -> result = chunk }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: genericErrorText
            } finally {
                translating = false
            }
        }
    }

    suspend fun readScreenText(): String = withContext(Dispatchers.IO) {
        val svc = RikkaAccessibilityService.instance ?: return@withContext ""
        val ourPkg = context.packageName
        val root = svc.windows
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() != ourPkg }
            .firstOrNull() ?: svc.rootInActiveWindow
        if (root == null || root.packageName?.toString() == ourPkg) return@withContext ""
        val lines = mutableListOf<String>()
        svc.traverseTree(
            root = root,
            filter = { n, _ ->
                n.isVisibleToUser &&
                    (n.text?.toString().orEmpty().isNotBlank() ||
                        n.contentDescription?.toString().orEmpty().isNotBlank())
            },
            cap = 1000,
        ) { n, _, _ ->
            n.text?.toString()?.takeIf { it.isNotBlank() }?.let { lines += it.trim() }
            n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { lines += it.trim() }
        }
        lines.distinct().joinToString("\n")
    }

    fun translateScreen() {
        if (translating) return
        screenStatus = null
        scope.launch {
            val text = readScreenText()
            if (text.isBlank()) {
                screenStatus = screenEmptyText
                return@launch
            }
            source = text
            doTranslate()
        }
    }

    fun toggleLive() {
        if (liveJob != null) {
            liveJob?.cancel()
            liveJob = null
            live = false
            return
        }
        live = true
        liveJob = scope.launch {
            var last = ""
            while (true) {
                if (RikkaAccessibilityService.instance == null) {
                    live = false
                    liveJob = null
                    a11yRunning = false
                    screenStatus = a11yRequiredText
                    break
                }
                val text = readScreenText()
                if (text.isNotBlank() && text != last && !translating) {
                    last = text
                    source = text
                    doTranslate()
                }
                delay(3000)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = WaveShape(),
        color = MaterialTheme.colorScheme.surface,
    ) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.translate_bubble_notification_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.translate_bubble_target_language),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    TextButton(onClick = { languageMenuOpen = true }) {
                        Text(getLanguageDisplayName(target))
                    }
                    DropdownMenu(
                        expanded = languageMenuOpen,
                        onDismissRequest = { languageMenuOpen = false },
                    ) {
                        Locales.forEach { locale ->
                            DropdownMenuItem(
                                text = { Text(getLanguageDisplayName(locale)) },
                                onClick = {
                                    target = locale
                                    languageMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { translateScreen() },
                    enabled = !translating,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.translate_bubble_screen))
                }
                OutlinedButton(
                    onClick = { toggleLive() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (live) stringResource(R.string.translate_bubble_live) + " ·"
                        else stringResource(R.string.translate_bubble_live)
                    )
                }
            }

            if (!a11yRunning) {
                Text(
                    text = a11yRequiredText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) {
                    Text(stringResource(R.string.translate_bubble_open_a11y))
                }
            } else if (screenStatus != null) {
                Text(
                    text = screenStatus.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                label = { Text(stringResource(R.string.translate_bubble_input_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
            )

            Button(
                onClick = { doTranslate() },
                enabled = !translating && source.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.translate_bubble_translate))
            }

            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = result,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(stringResource(R.string.translate_bubble_result_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = {
                        if (result.isNotBlank()) {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, result)))
                            }
                        }
                    },
                    enabled = result.isNotBlank(),
                ) {
                    Text(stringResource(R.string.translate_bubble_copy))
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(R.string.translate_bubble_dismiss))
                }
            }
        }
    }
    }
}

@Composable
private fun getLanguageDisplayName(locale: Locale): String = when (locale) {
    Locale.SIMPLIFIED_CHINESE -> stringResource(R.string.language_simplified_chinese)
    Locale.ENGLISH -> stringResource(R.string.language_english)
    Locale.TRADITIONAL_CHINESE -> stringResource(R.string.language_traditional_chinese)
    Locale.JAPANESE -> stringResource(R.string.language_japanese)
    Locale.KOREAN -> stringResource(R.string.language_korean)
    Locale.FRENCH -> stringResource(R.string.language_french)
    Locale.GERMAN -> stringResource(R.string.language_german)
    Locale.ITALIAN -> stringResource(R.string.language_italian)
    Locale.forLanguageTag("es-ES") -> stringResource(R.string.language_spanish)
    Locale.forLanguageTag("ar") -> stringResource(R.string.language_arabic)
    Locale.forLanguageTag("fa") -> stringResource(R.string.language_persian)
    Locale.forLanguageTag("ur") -> stringResource(R.string.language_urdu)
    else -> locale.getDisplayLanguage(Locale.getDefault())
}

private val Locales = listOf(
    Locale.SIMPLIFIED_CHINESE,
    Locale.ENGLISH,
    Locale.TRADITIONAL_CHINESE,
    Locale.JAPANESE,
    Locale.KOREAN,
    Locale.FRENCH,
    Locale.GERMAN,
    Locale.ITALIAN,
    Locale.forLanguageTag("es-ES"),
    Locale.forLanguageTag("ar"),
    Locale.forLanguageTag("fa"),
    Locale.forLanguageTag("ur"),
)
