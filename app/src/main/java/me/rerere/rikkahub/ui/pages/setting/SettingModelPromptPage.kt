package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.ui.icons.MingcuteIcons as HugeIcons
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.ChatUtilityPrompt
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.utils.plus

@Composable
internal fun PromptSettingsPage(settings: Settings, vm: SettingVM, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PromptSettingItem(
                title = stringResource(R.string.setting_model_page_prompt_translation),
                promptDescription = stringResource(R.string.setting_model_page_translate_prompt_vars),
                promptValue = settings.translatePrompt,
                onSavePrompt = { vm.updateUtilityPrompt(ChatUtilityPrompt.TRANSLATION, it) },
                defaultPrompt = DEFAULT_TRANSLATION_PROMPT,
                reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
                onUpdateReasoningLevel = { vm.updateTranslationThinkingBudget(it.budgetTokens) },
            )
        }
        item {
            PromptSettingItem(
                title = stringResource(R.string.setting_model_page_prompt_title),
                promptDescription = stringResource(R.string.setting_model_page_title_prompt_vars),
                promptValue = settings.titlePrompt,
                onSavePrompt = { vm.updateUtilityPrompt(ChatUtilityPrompt.TITLE, it) },
                defaultPrompt = DEFAULT_TITLE_PROMPT,
            )
        }
        item {
            PromptSettingItem(
                title = stringResource(R.string.setting_model_page_prompt_suggestion),
                promptDescription = stringResource(R.string.setting_model_page_suggestion_prompt_vars),
                promptValue = settings.suggestionPrompt,
                onSavePrompt = { vm.updateUtilityPrompt(ChatUtilityPrompt.SUGGESTION, it) },
                defaultPrompt = DEFAULT_SUGGESTION_PROMPT,
            )
        }
        item {
            PromptSettingItem(
                title = stringResource(R.string.setting_model_page_prompt_ocr),
                promptDescription = stringResource(R.string.setting_model_page_ocr_prompt_vars),
                promptValue = settings.ocrPrompt,
                onSavePrompt = { vm.updateUtilityPrompt(ChatUtilityPrompt.OCR, it) },
                defaultPrompt = DEFAULT_OCR_PROMPT,
            )
        }
        item {
            PromptSettingItem(
                title = stringResource(R.string.setting_model_page_prompt_compress),
                promptDescription = stringResource(R.string.setting_model_page_compress_prompt_vars),
                promptValue = settings.compressPrompt,
                onSavePrompt = { vm.updateUtilityPrompt(ChatUtilityPrompt.COMPRESSION, it) },
                defaultPrompt = DEFAULT_COMPRESS_PROMPT,
            )
        }
    }
}

@Composable
private fun PromptSettingItem(
    title: String,
    promptDescription: String,
    promptValue: String,
    onSavePrompt: (String) -> Unit,
    defaultPrompt: String,
    reasoningLevel: ReasoningLevel? = null,
    onUpdateReasoningLevel: ((ReasoningLevel) -> Unit)? = null,
) {
    var showEditor by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    CardGroup(title = { Text(title) }) {
        item(
            onClick = {
                draft = promptValue
                showEditor = true
            },
            headlineContent = {
                Text(
                    if (promptValue == defaultPrompt)
                        stringResource(R.string.setting_model_prompt_bundled)
                    else stringResource(R.string.setting_model_prompt_customized)
                )
            },
            trailingContent = {
                Icon(
                    HugeIcons.ArrowRight01,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        if (reasoningLevel != null && onUpdateReasoningLevel != null) {
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_thinking_budget)) },
                trailingContent = {
                    ReasoningButton(
                        reasoningLevel = reasoningLevel,
                        onUpdateReasoningLevel = onUpdateReasoningLevel,
                    )
                },
            )
        }
    }

    if (showEditor) {
        ModalBottomSheet(onDismissRequest = { showEditor = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    promptDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 15,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { draft = defaultPrompt }) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { showEditor = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        enabled = draft.isNotBlank(),
                        onClick = {
                            onSavePrompt(draft)
                            showEditor = false
                        },
                    ) {
                        Text(stringResource(R.string.assistant_page_save))
                    }
                }
            }
        }
    }
}
