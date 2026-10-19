package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.icons.MingcuteIcons as HugeIcons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceBoolean
import me.rerere.rikkahub.ui.pages.setting.components.CompactToggleGrid
import me.rerere.rikkahub.ui.pages.setting.components.CompactToggleOption
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPreferencesGeneralPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var createNewConversationOnStart by rememberSharedPreferenceBoolean(
        "create_new_conversation_on_start", true
    )

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    val items = mutableListOf(
        CompactToggleOption(
            stringResource(R.string.setting_display_page_create_new_conversation_on_start_title),
            stringResource(R.string.setting_display_page_create_new_conversation_on_start_desc),
            createNewConversationOnStart,
            { createNewConversationOnStart = it },
        ),
        CompactToggleOption(
            stringResource(R.string.setting_display_page_send_on_enter_title),
            stringResource(R.string.setting_display_page_send_on_enter_desc),
            displaySetting.sendOnEnter,
            { updateDisplaySetting(displaySetting.copy(sendOnEnter = it)) },
        ),
        CompactToggleOption(
            stringResource(R.string.setting_display_page_show_message_jumper_title),
            stringResource(R.string.setting_display_page_show_message_jumper_desc),
            displaySetting.showMessageJumper,
            { updateDisplaySetting(displaySetting.copy(showMessageJumper = it)) },
        ),
        CompactToggleOption(
            stringResource(R.string.setting_display_page_enable_auto_scroll_title),
            stringResource(R.string.setting_display_page_enable_auto_scroll_desc),
            displaySetting.enableAutoScroll,
            { updateDisplaySetting(displaySetting.copy(enableAutoScroll = it)) },
        ),
        CompactToggleOption(
            stringResource(R.string.setting_display_page_use_app_icon_style_loading_indicator_title),
            stringResource(R.string.setting_display_page_use_app_icon_style_loading_indicator_desc),
            displaySetting.useAppIconStyleLoadingIndicator,
            { updateDisplaySetting(displaySetting.copy(useAppIconStyleLoadingIndicator = it)) },
        ),
        CompactToggleOption(
            stringResource(R.string.setting_display_page_enable_blur_effect_title),
            stringResource(R.string.setting_display_page_enable_blur_effect_desc),
            displaySetting.enableBlurEffect,
            { updateDisplaySetting(displaySetting.copy(enableBlurEffect = it)) },
        ),
        CompactToggleOption(
            stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title),
            stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc),
            displaySetting.enableMessageGenerationHapticEffect,
            { updateDisplaySetting(displaySetting.copy(enableMessageGenerationHapticEffect = it)) },
        ),
        CompactToggleOption(
            stringResource(R.string.setting_display_page_skip_crop_image_title),
            stringResource(R.string.setting_display_page_skip_crop_image_desc),
            displaySetting.skipCropImage,
            { updateDisplaySetting(displaySetting.copy(skipCropImage = it)) },
        ),
        CompactToggleOption(
            stringResource(R.string.setting_display_page_paste_long_text_as_file_title),
            stringResource(R.string.setting_display_page_paste_long_text_as_file_desc),
            displaySetting.pasteLongTextAsFile,
            { updateDisplaySetting(displaySetting.copy(pasteLongTextAsFile = it)) },
        ),
        CompactToggleOption(
            stringResource(R.string.setting_display_page_volume_key_scroll_title),
            stringResource(R.string.setting_display_page_volume_key_scroll_desc),
            displaySetting.enableVolumeKeyScroll,
            { updateDisplaySetting(displaySetting.copy(enableVolumeKeyScroll = it)) },
        ),
    )
    if (displaySetting.showMessageJumper) {
        items.add(3, CompactToggleOption(
            stringResource(R.string.setting_display_page_message_jumper_position_title),
            stringResource(R.string.setting_display_page_message_jumper_position_desc),
            displaySetting.messageJumperOnLeft,
            { updateDisplaySetting(displaySetting.copy(messageJumperOnLeft = it)) },
        ))
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_page_chat_preferences)) },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("chatPrompts") {
                Column {
                    ChatPreferencesDestination(
                        title = stringResource(R.string.settings_default_prompts),
                        onClick = { navController.navigate(Screen.SettingChatPrompts) },
                    )
                    ChatPreferencesDestination(
                        title = stringResource(R.string.setting_home_prompt_library),
                        onClick = { navController.navigate(Screen.Prompts) },
                    )
                }
            }
            item("switches") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.setting_compact_hold_details),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CompactToggleGrid(items)
                }
            }
            if (displaySetting.pasteLongTextAsFile) {
                item("pasteThreshold") {
                    CompactParameterSlider(
                        title = stringResource(R.string.setting_display_page_paste_long_text_threshold_title),
                        value = displaySetting.pasteLongTextThreshold.toFloat(),
                        range = 100f..10000f,
                        valueText = displaySetting.pasteLongTextThreshold.toString(),
                        onValueChange = { updateDisplaySetting(displaySetting.copy(pasteLongTextThreshold = it.toInt())) },
                    )
                }
            }
            if (displaySetting.enableVolumeKeyScroll) {
                item("volumeRatio") {
                    CompactParameterSlider(
                        title = stringResource(R.string.setting_display_page_volume_key_scroll_ratio),
                        value = displaySetting.volumeKeyScrollRatio,
                        range = 0.25f..1f,
                        valueText = "${(displaySetting.volumeKeyScrollRatio * 100).toInt()}%",
                        onValueChange = { updateDisplaySetting(displaySetting.copy(volumeKeyScrollRatio = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactParameterSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.labelMedium)
                Text(valueText, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = value, onValueChange = onValueChange, valueRange = range)
        }
    }
}


@Composable
private fun ChatPreferencesDestination(title: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(HugeIcons.ArrowRight01, contentDescription = null)
        }
    }
}
