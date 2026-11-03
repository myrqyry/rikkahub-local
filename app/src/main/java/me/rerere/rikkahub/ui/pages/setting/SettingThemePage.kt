package me.rerere.rikkahub.ui.pages.setting

import android.content.ClipData
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import hct.Hct

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
                actions = {
                    ColorModeIcon(ColorMode.SYSTEM, colorMode, { colorMode = it }, HugeIcons.SmartPhone01,
                        stringResource(R.string.setting_page_color_mode_system))
                    ColorModeIcon(ColorMode.LIGHT, colorMode, { colorMode = it }, HugeIcons.Sun01,
                        stringResource(R.string.setting_page_color_mode_light))
                    ColorModeIcon(ColorMode.DARK, colorMode, { colorMode = it }, HugeIcons.Moon01,
                        stringResource(R.string.setting_page_color_mode_dark))
                    Spacer(Modifier.width(5.dp))
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("paletteControls") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.setting_theme_palette_source),
                                style = MaterialTheme.typography.titleSmall)
                            CompactSwitch(
                                label = stringResource(R.string.setting_theme_source_wallpaper),
                                checked = isWallpaperSource,
                                onCheckedChange = {
                                    if (it) chooseWallpaper() else chooseBackground()
                                },
                            )
                        }
                        HueControl(
                            title = stringResource(R.string.setting_theme_background_hue),
                            seed = lastBackgroundSeed,
                            enabled = !isWallpaperSource,
                            onCommit = { chooseBackground(it) },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompactSwitch(
                                label = stringResource(R.string.setting_theme_accent_override),
                                checked = settings.personalThemeAccentColor != null,
                                onCheckedChange = { enabled ->
                                    vm.updateSettings(settings.copy(
                                        dynamicColor = true,
                                        materialYouSourceColor = activeBackgroundSeed,
                                        personalThemeAccentColor = if (enabled)
                                            settings.personalThemeAccentColor ?: (personalPreview.primary.toArgb().toLong() and 0xFFFFFFFFL)
                                        else null,
                                    ))
                                },
                            )
                            CompactSwitch(
                                label = stringResource(R.string.setting_theme_amoled_short),
                                checked = amoledDarkMode,
                                onCheckedChange = { amoledDarkMode = it },
                            )
                        }
                        settings.personalThemeAccentColor?.let { accent ->
                            HueControl(
                                title = stringResource(R.string.setting_theme_accent_hue),
                                seed = accent,
                                enabled = true,
                                onCommit = {
                                    vm.updateSettings(settings.copy(
                                        dynamicColor = true,
                                        materialYouSourceColor = activeBackgroundSeed,
                                        personalThemeAccentColor = it,
                                    ))
                                },
                            )
                        }
                    }
                }
            }
            item("libraryHeader") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            stringResource(R.string.setting_theme_installed),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.setting_theme_hold_options),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                browserOpen = true
                                themePreview = null
                                if (catalog.isEmpty()) refreshCatalog()
                            },
                        ) {
                            Icon(HugeIcons.Colors,
                                contentDescription = stringResource(R.string.setting_theme_browse_colorway))
                        }
                        IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                            Icon(HugeIcons.FileImport,
                                contentDescription = stringResource(R.string.setting_theme_import_file))
                        }
                        IconButton(onClick = { editingTheme = null; showEditSheet = true }) {
                            Icon(HugeIcons.PlusSign,
                                contentDescription = stringResource(R.string.setting_theme_page_add_theme))
                        }
                    }
                }
            }
            // A normal LazyColumn row is enough for three compact orbs. No nested
            // scrolling grid, no full-height preference cards per saved palette.
            items((listOf<CustomTheme?>(null) + settings.customThemes).chunked(3),
                key = { row -> row.firstOrNull()?.id ?: "personal" },
            ) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    row.forEach { theme ->
                        val isPersonal = theme == null
                        ThemeOrbTile(
                            theme = theme,
                            name = theme?.name?.ifEmpty { "Unnamed" }
                                ?: stringResource(R.string.setting_theme_my_theme),
                            scheme = theme?.generateColorScheme(dark) ?: personalPreview,
                            selected = if (isPersonal) !customActive else customActive && settings.themeId == theme.id,
                            modifier = Modifier.weight(1f),
                            onSelect = {
                                if (isPersonal) {
                                    if (!settings.dynamicColor) {
                                        if (legacyMeristem) chooseBackground(legacySeed)
                                        else vm.updateSettings(settings.copy(dynamicColor = true))
                                    }
                                } else {
                                    vm.updateSettings(settings.copy(dynamicColor = false, themeId = theme.id))
                                }
                            },
                            onExport = if (theme == null) null else {
                                {
                                    scope.launch {
                                        clipboardManager.setClipEntry(
                                            ClipEntry(ClipData.newPlainText("theme", themeJson.encodeToString(theme)))
                                        )
                                    }
                                    toaster.show(exportSuccessMsg, type = ToastType.Success)
                                }
                            },
                            onEdit = if (theme == null) null else {
                                { editingTheme = theme; showEditSheet = true }
                            },
                            onDelete = if (theme == null) null else {
                                { deletingTheme = theme }
                            },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item("themeActions") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            browserOpen = true
                            themePreview = null
                            if (catalog.isEmpty()) refreshCatalog()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.setting_theme_browse_colorway)) }
                    FilledTonalButton(
                        onClick = { showImportDialog = true },
                    ) { Text(stringResource(R.string.setting_theme_paste_theme)) }
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
                val wasActive = !settings.dynamicColor && settings.themeId == theme.id
                vm.updateSettings(
                    settings.copy(
                        customThemes = newThemes,
                        themeId = if (wasActive) MERISTEM_THEME_ID else settings.themeId,
                        dynamicColor = if (wasActive) true else settings.dynamicColor,
                    )
                )
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
private fun ColorModeIcon(
    mode: ColorMode,
    current: ColorMode,
    onSelect: (ColorMode) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
) {
    val selected = current == mode
    IconButton(
        onClick = { onSelect(mode) },
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(
            if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        ),
    ) {
        Icon(
            icon, contentDescription = name,
            modifier = Modifier.size(19.dp),
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompactSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.graphicsLayer(scaleX = 0.79f, scaleY = 0.79f),
        )
    }
}

private val rainbow = Brush.horizontalGradient(
    listOf(
        Color(0xFFE86F74), Color(0xFFE5BB65), Color(0xFF78CA86),
        Color(0xFF69C9C9), Color(0xFF7D9BDE), Color(0xFFCA83CD),
        Color(0xFFE86F74),
    )
)

internal fun hueOf(seed: Long): Float = Hct.fromInt(seed.toInt()).hue.toFloat()

internal fun withHue(seed: Long, hue: Float): Long {
    val current = Hct.fromInt(seed.toInt())
    return Hct.from(hue.toDouble(), current.chroma, current.tone).toInt().toLong() and 0xFFFFFFFFL
}

@Composable
private fun HueControl(
    title: String,
    seed: Long,
    enabled: Boolean,
    onCommit: (Long) -> Unit,
) {
    var hue by remember(seed) { mutableFloatStateOf(hueOf(seed)) }
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Box(
                modifier = Modifier.size(15.dp).clip(CircleShape)
                    .background(Color(withHue(seed, hue).toInt()))
            )
        }
        Slider(
            value = hue,
            onValueChange = { hue = it },
            onValueChangeFinished = { onCommit(withHue(seed, hue)) },
            valueRange = 0f..359.9f,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(37.dp),
            track = {
                Box(
                    modifier = Modifier.fillMaxWidth().height(11.dp)
                        .clip(RoundedCornerShape(10.dp)).background(rainbow)
                )
            },
        )
    }
}

@Composable
private fun ThemeOrbTile(
    theme: CustomTheme?,
    name: String,
    scheme: androidx.compose.material3.ColorScheme,
    selected: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
    onExport: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(stiffness = 340f),
        label = "theme-orb-scale",
    )
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = if (theme == null) null else {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMenu = true
                        }
                    },
                    onLongClickLabel = if (theme == null) null else "Theme options",
                )
                .padding(vertical = 9.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier.size(66.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                    .clip(CircleShape)
                    .background(scheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize().padding(4.dp).clip(CircleShape)) {
                    drawRect(scheme.primaryContainer)
                    drawRect(scheme.secondaryContainer, topLeft = Offset(size.width / 2f, 0f),
                        size = androidx.compose.ui.geometry.Size(size.width / 2f, size.height))
                    drawRect(scheme.tertiaryContainer, topLeft = Offset(size.width / 2f, size.height / 2f),
                        size = androidx.compose.ui.geometry.Size(size.width / 2f, size.height / 2f))
                    drawCircle(scheme.primary, radius = 10.dp.toPx(), center = center)
                }
                if (selected) {
                    Surface(
                        shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.BottomEnd).size(20.dp),
                    ) {
                        Icon(HugeIcons.Tick01, null, modifier = Modifier.padding(3.dp),
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            Text(
                name, style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (onEdit != null) DropdownMenuItem(
                text = { Text(stringResource(R.string.setting_theme_page_edit_theme)) },
                onClick = { showMenu = false; onEdit() },
            )
            if (onExport != null) DropdownMenuItem(
                text = { Text(stringResource(R.string.setting_theme_page_export_theme)) },
                onClick = { showMenu = false; onExport() },
            )
            if (onDelete != null) DropdownMenuItem(
                text = { Text(stringResource(R.string.setting_theme_page_delete_theme_title)) },
                onClick = { showMenu = false; onDelete() },
            )
        }
    }
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
