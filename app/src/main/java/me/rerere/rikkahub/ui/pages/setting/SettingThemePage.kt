package me.rerere.rikkahub.ui.pages.setting

import android.content.ClipData
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.ui.icons.MingcuteIcons as HugeIcons
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.setting.components.ColorwayBrowserDialog
import me.rerere.rikkahub.ui.pages.setting.components.ThemeChoiceCard
import me.rerere.rikkahub.ui.theme.ColorwayCatalog
import me.rerere.rikkahub.ui.theme.ColorwayCatalogEntry
import me.rerere.rikkahub.ui.theme.ThemeImport
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.MERISTEM_THEME_ID
import me.rerere.rikkahub.ui.theme.MATERIAL_YOU_FALLBACK_COLOR
import me.rerere.rikkahub.ui.theme.personalPalette
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.ColorMode
import me.rerere.rikkahub.ui.hooks.rememberColorMode
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.theme.findThemeFamily
import me.rerere.rikkahub.ui.theme.nextThemeAccent
import me.rerere.rikkahub.ui.theme.rememberRikkaNeumorphicColors
import dev.neumorphic.components.NeumorphicSegmentedControl
import me.rerere.rikkahub.ui.theme.nextThemeVariation
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

private val themeJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingThemePage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var colorMode by rememberColorMode()
    var amoledDarkMode by rememberAmoledDarkMode()
    val clipboardManager = LocalClipboard.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val exportSuccessMsg = stringResource(R.string.setting_theme_page_export_success)
    val importSuccessMsg = stringResource(R.string.setting_theme_page_import_success)
    val dark = LocalDarkMode.current
    val context = LocalContext.current
    val customActive = settings.customThemes.any { it.id == settings.themeId } && !settings.dynamicColor
    val legacyMeristem = !settings.dynamicColor && settings.themeId == MERISTEM_THEME_ID
    val family = findThemeFamily(MERISTEM_THEME_ID)
    val legacySeed = family?.accents?.find { it.id == settings.themeAccent }?.seed
        ?: family?.defaultAccent?.seed ?: MATERIAL_YOU_FALLBACK_COLOR
    val activeBackgroundSeed = settings.materialYouSourceColor
        ?: if (legacyMeristem) legacySeed else null
    val isWallpaperSource = activeBackgroundSeed == null
    val lastBackgroundSeed = settings.personalLastBackgroundColor
        ?: settings.materialYouSourceColor ?: legacySeed
    val wallpaperPalette = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else CustomTheme(primaryColorArgb = MATERIAL_YOU_FALLBACK_COLOR).generateColorScheme(dark)
    val personalPreview = personalPalette(
        dark = dark,
        wallpaper = wallpaperPalette,
        backgroundSeed = activeBackgroundSeed,
        accentOverride = settings.personalThemeAccentColor,
    )

    fun chooseWallpaper() {
        vm.updateSettings(
            settings.copy(
                dynamicColor = true,
                materialYouSourceColor = null,
                personalLastBackgroundColor = lastBackgroundSeed,
            )
        )
    }
    fun chooseBackground(seed: Long = lastBackgroundSeed) {
        vm.updateSettings(
            settings.copy(
                dynamicColor = true,
                materialYouSourceColor = seed,
                personalLastBackgroundColor = seed,
            )
        )
    }
    var showEditSheet by remember { mutableStateOf(false) }
    var editingTheme by remember { mutableStateOf<CustomTheme?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var deletingTheme by remember { mutableStateOf<CustomTheme?>(null) }
    var showSavedThemes by rememberSaveable { mutableStateOf(false) }
    var editBackgroundSeed by rememberSaveable { mutableStateOf(false) }
    var editAccentSeed by rememberSaveable { mutableStateOf(false) }

    var browserOpen by rememberSaveable { mutableStateOf(false) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var catalog by remember { mutableStateOf<List<ColorwayCatalogEntry>>(emptyList()) }
    var downloadingPath by remember { mutableStateOf<String?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var themePreview by remember { mutableStateOf<CustomTheme?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            ThemeImport.parse(ThemeImport.readLimited(input))
                        } ?: error("Could not open this file.")
                    }
                }
                result.onSuccess { imported ->
                    val installed = imported.copy(id = Uuid.random().toString())
                    vm.updateSettings(
                        settings.copy(
                            customThemes = settings.customThemes + installed,
                            themeId = installed.id,
                            dynamicColor = false,
                        )
                    )
                    showSavedThemes = true
                    toaster.show(importSuccessMsg, type = ToastType.Success)
                }.onFailure { toaster.show(it.message ?: "Theme import failed.", type = ToastType.Error) }
            }
        }
    }

    fun refreshCatalog() {
        if (catalogLoading) return
        catalogLoading = true
        catalogError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { ColorwayCatalog.fetchCatalog() } }
            catalogLoading = false
            result.onSuccess { catalog = it }
                .onFailure { catalogError = it.message ?: "Could not load theme catalog." }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_page_appearance)) },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("displayControls") {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setting_page_color_mode),
                        modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    NeumorphicSegmentedControl(
                        items = listOf(
                            stringResource(R.string.setting_page_color_mode_system),
                            stringResource(R.string.setting_page_color_mode_light),
                            stringResource(R.string.setting_page_color_mode_dark),
                        ),
                        selectedIndex = ColorMode.entries.indexOf(colorMode).coerceAtLeast(0),
                        onItemSelected = { index -> colorMode = ColorMode.entries[index] },
                        modifier = Modifier.fillMaxWidth(),
                        colors = rememberRikkaNeumorphicColors(),
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.setting_display_page_amoled_dark_mode_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = stringResource(R.string.setting_appearance_amoled_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = amoledDarkMode,
                                onCheckedChange = { amoledDarkMode = it },
                            )
                        }
                    }
                }
            }

            item("personalTheme") {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setting_theme_your_theme),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    ThemeChoiceCard(
                        title = stringResource(R.string.setting_theme_my_theme),
                        description = stringResource(R.string.setting_theme_my_theme_description),
                        scheme = personalPreview,
                        selected = !customActive,
                        onClick = {
                            if (!settings.dynamicColor) {
                                if (legacyMeristem) chooseBackground(legacySeed)
                                else vm.updateSettings(settings.copy(dynamicColor = true))
                            }
                        },
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.setting_theme_palette_source),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            NeumorphicSegmentedControl(
                                items = listOf(
                                    stringResource(R.string.setting_theme_source_wallpaper),
                                    stringResource(R.string.setting_theme_source_background),
                                ),
                                selectedIndex = if (isWallpaperSource) 0 else 1,
                                onItemSelected = { index ->
                                    editBackgroundSeed = false
                                    if (index == 0) chooseWallpaper() else chooseBackground()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = rememberRikkaNeumorphicColors(),
                            )
                            if (isWallpaperSource) {
                                Text(
                                    text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                        stringResource(R.string.setting_theme_wallpaper_active)
                                    else stringResource(R.string.setting_theme_wallpaper_unavailable),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    stringResource(R.string.setting_theme_background_color_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(
                                            Modifier.size(30.dp).clip(CircleShape)
                                                .background(Color(requireNotNull(activeBackgroundSeed).toInt()))
                                        )
                                        Text(
                                            "#%06X".format(activeBackgroundSeed.toInt() and 0xFFFFFF),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                    FilledTonalButton(onClick = { editBackgroundSeed = !editBackgroundSeed }) {
                                        Text(stringResource(
                                            if (editBackgroundSeed) R.string.setting_theme_finish_seed
                                            else R.string.setting_theme_edit_seed
                                        ))
                                    }
                                }
                                if (editBackgroundSeed) {
                                    ColorPickerRow(
                                        color = Color(activeBackgroundSeed.toInt()),
                                        onColorChange = {
                                            chooseBackground(it.toArgb().toLong() and 0xFFFFFFFFL)
                                        },
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        stringResource(R.string.setting_theme_accent_override),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        stringResource(R.string.setting_theme_accent_override_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = settings.personalThemeAccentColor != null,
                                    onCheckedChange = { enabled ->
                                        editAccentSeed = false
                                        vm.updateSettings(settings.copy(
                                            dynamicColor = true,
                                            materialYouSourceColor = activeBackgroundSeed,
                                            personalThemeAccentColor = if (enabled)
                                                settings.personalThemeAccentColor
                                                    ?: (personalPreview.primary.toArgb().toLong() and 0xFFFFFFFFL)
                                            else null,
                                        ))
                                    }
                                )
                            }
                            settings.personalThemeAccentColor?.let { accent ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(accent.toInt())))
                                        Text("#%06X".format(accent.toInt() and 0xFFFFFF))
                                    }
                                    FilledTonalButton(onClick = { editAccentSeed = !editAccentSeed }) {
                                        Text(stringResource(
                                            if (editAccentSeed) R.string.setting_theme_finish_seed
                                            else R.string.setting_theme_edit_seed
                                        ))
                                    }
                                }
                                if (editAccentSeed) {
                                    ColorPickerRow(
                                        color = Color(accent.toInt()),
                                        onColorChange = {
                                            vm.updateSettings(settings.copy(
                                                dynamicColor = true,
                                                materialYouSourceColor = activeBackgroundSeed,
                                                personalThemeAccentColor = it.toArgb().toLong() and 0xFFFFFFFFL,
                                            ))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item("themeLibraryActions") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setting_theme_library_title),
                        modifier = Modifier.padding(horizontal = 6.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                browserOpen = true
                                themePreview = null
                                if (catalog.isEmpty()) refreshCatalog()
                            },
                        ) {
                            Text(stringResource(R.string.setting_theme_browse_colorway))
                        }
                        FilledTonalButton(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                        ) {
                            Icon(HugeIcons.FileImport, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.setting_theme_import_file))
                        }
                    }
                }
            }

            item("customThemesHeader") {
                Surface(
                    onClick = { showSavedThemes = !showSavedThemes },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = stringResource(R.string.setting_theme_page_custom_themes),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.setting_theme_saved_palettes_count, settings.customThemes.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = if (showSavedThemes || customActive) "−" else "+",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }

            if (showSavedThemes || customActive) {
                item("customThemeActions") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(onClick = { showImportDialog = true }) {
                            Icon(HugeIcons.FileImport, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.setting_theme_paste_theme))
                        }
                        FilledTonalButton(
                            onClick = {
                                editingTheme = null
                                showEditSheet = true
                            },
                        ) {
                            Icon(HugeIcons.PlusSign, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.setting_theme_page_add_theme))
                        }
                    }
                }

                if (settings.customThemes.isEmpty()) {
                    item("emptyCustomThemes") {
                        Text(
                            text = stringResource(R.string.setting_theme_page_no_custom_themes),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(settings.customThemes, key = { it.id }) { theme ->
                    CustomThemeItem(
                        theme = theme,
                        isSelected = customActive && settings.themeId == theme.id,
                        onSelect = {
                            vm.updateSettings(settings.copy(dynamicColor = false, themeId = theme.id))
                        },
                        onExport = {
                            val json = themeJson.encodeToString(theme)
                            scope.launch {
                                clipboardManager.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("theme", json))
                                )
                            }
                            toaster.show(exportSuccessMsg, type = ToastType.Success)
                        },
                        onEdit = {
                            editingTheme = theme
                            showEditSheet = true
                        },
                        onDelete = { deletingTheme = theme },
                    )
                }
            }
        }
    }

    if (showEditSheet) {
        CustomThemeEditSheet(
            theme = editingTheme,
            onDismiss = { showEditSheet = false },
            onSave = { theme ->
                val newThemes = if (editingTheme != null) {
                    settings.customThemes.map { if (it.id == theme.id) theme else it }
                } else {
                    settings.customThemes + theme
                }
                vm.updateSettings(
                    settings.copy(
                        customThemes = newThemes,
                        themeId = theme.id,
                        dynamicColor = false
                    )
                )
                showEditSheet = false
            }
        )
    }

    if (showImportDialog) {
        ImportThemeDialog(
            onDismiss = { showImportDialog = false },
            onImport = { theme ->
                val importedTheme = theme.copy(id = Uuid.random().toString())
                vm.updateSettings(
                    settings.copy(
                        customThemes = settings.customThemes + importedTheme,
                        themeId = importedTheme.id,
                        dynamicColor = false
                    )
                )
                showImportDialog = false
                toaster.show(importSuccessMsg, type = ToastType.Success)
            }
        )
    }

    if (browserOpen) {
        ColorwayBrowserDialog(
            entries = catalog,
            loading = catalogLoading,
            error = catalogError,
            downloadingPath = downloadingPath,
            downloadError = downloadError,
            preview = themePreview,
            installedKeys = settings.customThemes.mapNotNull { it.sourceKey }.toSet(),
            onRefresh = { refreshCatalog() },
            onBackToCatalog = { themePreview = null },
            onInstall = {
                val candidate = themePreview
                if (candidate != null) {
                    val existing = settings.customThemes.firstOrNull {
                        it.source == "Colorway" && it.sourceKey == candidate.sourceKey
                    }
                    val installed = candidate.copy(id = existing?.id ?: Uuid.random().toString())
                    val updated = if (existing != null) {
                        settings.customThemes.map { if (it.id == existing.id) installed else it }
                    } else settings.customThemes + installed
                    vm.updateSettings(
                        settings.copy(customThemes = updated, themeId = installed.id, dynamicColor = false)
                    )
                    themePreview = null
                    browserOpen = false
                    showSavedThemes = true
                    toaster.show(importSuccessMsg, type = ToastType.Success)
                }
            },
            onDismiss = { if (downloadingPath == null) { browserOpen = false; themePreview = null } },
            onDownload = { entry ->
                if (downloadingPath == null) {
                    downloadingPath = entry.id
                    downloadError = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { ColorwayCatalog.download(entry) } }
                        downloadingPath = null
                        result.onSuccess { themePreview = it
                        }.onFailure { downloadError = it.message ?: "Unable to download theme." }
                    }
                }
            },
        )
    }

    RikkaConfirmDialog(
        show = deletingTheme != null,
        title = stringResource(R.string.setting_theme_page_delete_theme_title),
        confirmText = stringResource(android.R.string.ok),
        dismissText = stringResource(android.R.string.cancel),
        onConfirm = {
            deletingTheme?.let { theme ->
                val newThemes = settings.customThemes.filter { it.id != theme.id }
                val newThemeId = if (settings.themeId == theme.id) MERISTEM_THEME_ID else settings.themeId
                vm.updateSettings(settings.copy(customThemes = newThemes, themeId = newThemeId))
            }
            deletingTheme = null
        },
        onDismiss = { deletingTheme = null },
        text = {
            Text(stringResource(R.string.setting_theme_page_delete_theme_message))
        }
    )
}

@Composable
private fun CustomThemeItem(
    theme: CustomTheme,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val darkMode = LocalDarkMode.current
    val scheme = theme.generateColorScheme(darkMode)

    ListItem(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect() },
        headlineContent = { Text(theme.name.ifEmpty { "Unnamed" }) },
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .clip(CircleShape)
                        .size(40.dp)
                ) {
                    drawRect(color = scheme.primaryContainer, size = size)
                    drawRect(
                        color = scheme.secondaryContainer,
                        size = size,
                        topLeft = Offset(x = size.width / 2, y = 0f)
                    )
                    drawRect(
                        color = scheme.tertiaryContainer,
                        size = size,
                        topLeft = Offset(x = size.width / 2, y = size.height / 2)
                    )
                    drawCircle(
                        color = scheme.primary,
                        radius = if (isSelected) 10.dp.toPx() else 6.dp.toPx(),
                        center = Offset(x = size.width / 2, y = size.height / 2)
                    )
                }
                if (isSelected) {
                    Icon(
                        HugeIcons.Tick01,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onExport) {
                    Icon(HugeIcons.Copy01, null)
                }
                IconButton(onClick = onEdit) {
                    Icon(HugeIcons.Edit02, null)
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        HugeIcons.Delete02,
                        null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        colors = CustomColors.listItemColors,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomThemeEditSheet(
    theme: CustomTheme?,
    onDismiss: () -> Unit,
    onSave: (CustomTheme) -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var currentTheme by remember {
        mutableStateOf(theme ?: CustomTheme())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = if (theme == null) stringResource(R.string.setting_theme_page_create_theme)
                else stringResource(R.string.setting_theme_page_edit_theme),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = currentTheme.name,
                    onValueChange = { currentTheme = currentTheme.copy(name = it) },
                    label = { Text(stringResource(R.string.setting_theme_page_theme_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Text(
                    text = stringResource(R.string.setting_theme_page_primary_color),
                    style = MaterialTheme.typography.titleSmall,
                )
                ColorPickerRow(
                    color = Color(currentTheme.primaryColorArgb.toInt()),
                    onColorChange = {
                        currentTheme = currentTheme.copy(primaryColorArgb = it.toArgb().toLong() and 0xFFFFFFFFL, importedPalette = null)
                    }
                )

                Text(
                    text = stringResource(R.string.setting_theme_page_secondary_color),
                    style = MaterialTheme.typography.titleSmall,
                )
                ColorPickerRow(
                    color = if (currentTheme.secondaryColorArgb != null) {
                        Color(currentTheme.secondaryColorArgb!!.toInt())
                    } else {
                        Color(currentTheme.generateColorScheme(false).secondary.toArgb())
                    },
                    onColorChange = {
                        currentTheme = currentTheme.copy(secondaryColorArgb = it.toArgb().toLong() and 0xFFFFFFFFL, importedPalette = null)
                    }
                )

                Text(
                    text = stringResource(R.string.setting_theme_page_tertiary_color),
                    style = MaterialTheme.typography.titleSmall,
                )
                ColorPickerRow(
                    color = if (currentTheme.tertiaryColorArgb != null) {
                        Color(currentTheme.tertiaryColorArgb!!.toInt())
                    } else {
                        Color(currentTheme.generateColorScheme(false).tertiary.toArgb())
                    },
                    onColorChange = {
                        currentTheme = currentTheme.copy(tertiaryColorArgb = it.toArgb().toLong() and 0xFFFFFFFFL, importedPalette = null)
                    }
                )

                ThemePreview(currentTheme)
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(currentTheme) },
                    enabled = currentTheme.name.isNotBlank()
                ) {
                    Text(stringResource(R.string.setting_theme_page_save))
                }
            }
        }
    }
}

@Composable
private fun ImportThemeDialog(
    onDismiss: () -> Unit,
    onImport: (CustomTheme) -> Unit,
) {
    var jsonText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_theme_page_import_theme)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = {
                        jsonText = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.setting_theme_import_paste_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { msg -> { Text(msg) } },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val theme = ThemeImport.parse(jsonText)
                        onImport(theme)
                    } catch (e: Exception) {
                        errorMessage = e.message
                    }
                },
                enabled = jsonText.isNotBlank()
            ) {
                Text(stringResource(R.string.setting_theme_page_import_theme))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun ColorPickerRow(
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    val hsl = remember(color) {
        FloatArray(3).also { ColorUtils.colorToHSL(color.toArgb(), it) }
    }
    var hue by remember(color) { mutableFloatStateOf(hsl[0]) }
    var saturation by remember(color) { mutableFloatStateOf(hsl[1]) }
    var lightness by remember(color) { mutableFloatStateOf(hsl[2]) }
    var hslCode by remember(color) { mutableStateOf(formatHslCode(hsl[0], hsl[1], hsl[2])) }
    var hslCodeError by remember(color) { mutableStateOf(false) }

    fun updateColor(newHue: Float, newSaturation: Float, newLightness: Float) {
        hue = newHue
        saturation = newSaturation
        lightness = newLightness
        hslCode = formatHslCode(newHue, newSaturation, newLightness)
        hslCodeError = false
        onColorChange(Color(ColorUtils.HSLToColor(floatArrayOf(newHue, newSaturation, newLightness))))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                drawCircle(color = color)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("H", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
                    Slider(
                        value = hue,
                        onValueChange = {
                            updateColor(it, saturation, lightness)
                        },
                        valueRange = 0f..360f,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("S", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
                    Slider(
                        value = saturation,
                        onValueChange = {
                            updateColor(hue, it, lightness)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("L", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
                    Slider(
                        value = lightness,
                        onValueChange = {
                            updateColor(hue, saturation, it)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        OutlinedTextField(
            value = hslCode,
            onValueChange = { value ->
                hslCode = value
                val parsedHsl = parseHslCode(value)
                hslCodeError = parsedHsl == null
                if (parsedHsl != null) {
                    hue = parsedHsl[0]
                    saturation = parsedHsl[1]
                    lightness = parsedHsl[2]
                    onColorChange(Color(ColorUtils.HSLToColor(parsedHsl)))
                }
            },
            label = { Text("HSL") },
            placeholder = { Text("hsl(267 36% 48%)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = hslCodeError,
            supportingText = if (hslCodeError) {
                { Text("Use hsl(267 36% 48%)") }
            } else {
                null
            },
        )
    }
}

private val hslNumberRegex = Regex("""[-+]?\d*\.?\d+""")

private fun parseHslCode(value: String): FloatArray? {
    val values = buildList {
        for (match in hslNumberRegex.findAll(value)) {
            add(match.value.toFloatOrNull() ?: return null)
            if (size == 3) break
        }
    }

    if (values.size != 3) return null

    val hue = values[0].coerceIn(0f, 360f)
    val saturation = parseHslPercentOrFraction(values[1]) ?: return null
    val lightness = parseHslPercentOrFraction(values[2]) ?: return null

    return floatArrayOf(hue, saturation, lightness)
}

private fun parseHslPercentOrFraction(value: Float): Float? {
    if (!value.isFinite()) return null
    return if (value > 1f) {
        (value / 100f).coerceIn(0f, 1f)
    } else {
        value.coerceIn(0f, 1f)
    }
}

private fun formatHslCode(hue: Float, saturation: Float, lightness: Float): String {
    return "hsl(${hue.roundToInt()} ${(saturation * 100).roundToInt()}% ${(lightness * 100).roundToInt()}%)"
}

@Composable
private fun ThemePreview(theme: CustomTheme) {
    val darkMode = LocalDarkMode.current
    val scheme = theme.generateColorScheme(darkMode)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.setting_theme_page_preview),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ColorSwatch(scheme.primary, "P")
            ColorSwatch(scheme.secondary, "S")
            ColorSwatch(scheme.tertiary, "T")
            ColorSwatch(scheme.primaryContainer, "PC")
            ColorSwatch(scheme.secondaryContainer, "SC")
            ColorSwatch(scheme.surface, "Sf")
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
        ) {
            drawCircle(color = color)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
