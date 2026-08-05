package me.rerere.rikkahub.ui.pages.setting.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.core.net.toUri
import com.dokar.sonner.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.locallm.ModelInstall
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.tts.kitten.KittenTtsBundle
import me.rerere.tts.kitten.KittenTtsCatalog
import me.rerere.tts.kitten.KittenTtsConfig
import me.rerere.tts.pocket.PocketTtsBundle
import me.rerere.tts.pocket.PocketTtsCatalog
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.qwen3.Qwen3TtsCatalog
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import java.io.File

@Composable
fun TTSProviderConfigure(
    setting: TTSProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        // Provider type selector
        var expanded by remember { mutableStateOf(false) }
        val providers = remember {
            val types = TTSProviderSetting.Types
            val local = types.filter {
                it == TTSProviderSetting.SystemTTS::class ||
                    it == TTSProviderSetting.NekoSpeakTts::class ||
                    it == TTSProviderSetting.PocketTts::class ||
                    it == TTSProviderSetting.KittenTts::class ||
                    it == TTSProviderSetting.Qwen3Tts::class
            }
            val cloud = types.filter {
                it != TTSProviderSetting.SystemTTS::class &&
                    it != TTSProviderSetting.NekoSpeakTts::class &&
                    it != TTSProviderSetting.PocketTts::class &&
                    it != TTSProviderSetting.KittenTts::class &&
                    it != TTSProviderSetting.Qwen3Tts::class
            }
            local + cloud
        }

        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_provider_type)) },
            description = { Text(stringResource(R.string.setting_tts_page_provider_type_description)) },
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = when (setting) {
                        is TTSProviderSetting.OpenAI -> "OpenAI"
                        is TTSProviderSetting.Gemini -> "Gemini"
                        is TTSProviderSetting.SystemTTS -> "System TTS (Local)"
                        is TTSProviderSetting.MiniMax -> "MiniMax"
                        is TTSProviderSetting.Qwen -> "Qwen"
                        is TTSProviderSetting.Groq -> "Groq"
                        is TTSProviderSetting.XAI -> "xAI"
                        is TTSProviderSetting.MiMo -> "MiMo"
                        is TTSProviderSetting.Step -> "Step"
                        is TTSProviderSetting.ElevenLabs -> "ElevenLabs"
                        is TTSProviderSetting.FishAudio -> "Fish Audio"
                        is TTSProviderSetting.NekoSpeakTts -> "NekoSpeak (Local)"
                        is TTSProviderSetting.PocketTts -> "Pocket TTS (Local)"
                        is TTSProviderSetting.KittenTts -> "Kitten TTS (Local)"
                        is TTSProviderSetting.Qwen3Tts -> "Qwen3 TTS (Local)"
                    },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    providers.forEach { providerClass ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (providerClass) {
                                        TTSProviderSetting.OpenAI::class -> "OpenAI"
                                        TTSProviderSetting.Gemini::class -> "Gemini"
                                        TTSProviderSetting.SystemTTS::class -> "System TTS (Local)"
                                        TTSProviderSetting.MiniMax::class -> "MiniMax"
                                        TTSProviderSetting.Qwen::class -> "Qwen"
                                        TTSProviderSetting.Groq::class -> "Groq"
                                        TTSProviderSetting.XAI::class -> "xAI"
                                        TTSProviderSetting.MiMo::class -> "MiMo"
                                        TTSProviderSetting.ElevenLabs::class -> "ElevenLabs"
                                        TTSProviderSetting.FishAudio::class -> "Fish Audio"
                                        TTSProviderSetting.Step::class -> "Step"
                                        TTSProviderSetting.NekoSpeakTts::class -> "NekoSpeak (Local)"
                                        TTSProviderSetting.PocketTts::class -> "Pocket TTS (Local)"
                                        TTSProviderSetting.KittenTts::class -> "Kitten TTS (Local)"
                                        TTSProviderSetting.Qwen3Tts::class -> "Qwen3 TTS (Local)"
                                        else -> providerClass.simpleName ?: "Unknown"
                                    }
                                )
                            },
                            onClick = {
                                expanded = false
                                val newSetting = when (providerClass) {
                                    TTSProviderSetting.OpenAI::class -> TTSProviderSetting.OpenAI(
                                        id = setting.id,
                                        name = "OpenAI TTS"
                                    )

                                    TTSProviderSetting.Gemini::class -> TTSProviderSetting.Gemini(
                                        id = setting.id,
                                        name = "Gemini TTS"
                                    )

                                    TTSProviderSetting.SystemTTS::class -> TTSProviderSetting.SystemTTS(
                                        id = setting.id,
                                        name = "System TTS"
                                    )

                                    TTSProviderSetting.MiniMax::class -> TTSProviderSetting.MiniMax(
                                        id = setting.id,
                                        name = "MiniMax TTS"
                                    )

                                    TTSProviderSetting.Qwen::class -> TTSProviderSetting.Qwen(
                                        id = setting.id,
                                        name = "Qwen TTS"
                                    )

                                    TTSProviderSetting.Groq::class -> TTSProviderSetting.Groq(
                                        id = setting.id,
                                        name = "Groq TTS"
                                    )

                                    TTSProviderSetting.XAI::class -> TTSProviderSetting.XAI(
                                        id = setting.id,
                                        name = "xAI TTS"
                                    )

                                    TTSProviderSetting.MiMo::class -> TTSProviderSetting.MiMo(
                                        id = setting.id,
                                        name = "MiMo TTS"
                                    )
                                    TTSProviderSetting.ElevenLabs::class -> TTSProviderSetting.ElevenLabs(
                                        id = setting.id,
                                        name = "ElevenLabs TTS"
                                    )

                                    TTSProviderSetting.FishAudio::class -> TTSProviderSetting.FishAudio(
                                        id = setting.id,
                                        name = "Fish Audio TTS"
                                    )

                                    TTSProviderSetting.Step::class -> TTSProviderSetting.Step(
                                        id = setting.id,
                                        name = "Step TTS"
                                    )

                                    TTSProviderSetting.NekoSpeakTts::class -> TTSProviderSetting.NekoSpeakTts(
                                        id = setting.id,
                                        name = "NekoSpeak TTS"
                                    )

                                    TTSProviderSetting.PocketTts::class -> TTSProviderSetting.PocketTts(
                                        id = setting.id,
                                        name = "Pocket TTS (Local)"
                                    )

                                    TTSProviderSetting.KittenTts::class -> TTSProviderSetting.KittenTts(
                                        id = setting.id,
                                        name = "Kitten TTS (Local)"
                                    )

                                    TTSProviderSetting.Qwen3Tts::class -> TTSProviderSetting.Qwen3Tts(
                                        id = setting.id,
                                        name = "Qwen3 TTS (Local)"
                                    )

                                    else -> setting
                                }
                                onValueChange(newSetting)
                            }
                        )
                    }
                }
            }
        }

        // Name
        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_name)) },
            description = { Text(stringResource(R.string.setting_tts_page_name_description)) }
        ) {
            OutlinedTextField(
                value = setting.name,
                onValueChange = { newName ->
                    onValueChange(setting.copyProvider(name = newName))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_name_placeholder)) }
            )
        }

        // Provider-specific fields
        when (setting) {
            is TTSProviderSetting.OpenAI -> OpenAITTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Gemini -> GeminiTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.MiniMax -> MiniMaxTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.SystemTTS -> SystemTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Qwen -> QwenTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Groq -> GroqTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.XAI -> XAITTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.MiMo -> MiMoTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.ElevenLabs -> ElevenLabsTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.FishAudio -> FishAudioTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Step -> StepTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.NekoSpeakTts -> NekoSpeakTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.PocketTts -> PocketTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.KittenTts -> KittenTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Qwen3Tts -> Qwen3TTSConfiguration(setting, onValueChange)
        }
    }
}

@Composable
private fun OpenAITTSConfiguration(
    setting: TTSProviderSetting.OpenAI,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_api_key_placeholder_openai)) },
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_model_placeholder_openai)) }
        )
    }

    // Voice
    var voiceExpanded by remember { mutableStateOf(false) }
    val voices = listOf(
        "alloy", "ash", "ballad", "coral", "echo", "fable", "nova",
        "onyx", "sage", "shimmer", "verse", "marin", "cedar"
    )

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = voiceExpanded,
            onExpandedChange = { voiceExpanded = !voiceExpanded }
        ) {
            OutlinedTextField(
                value = setting.voice,
                onValueChange = { newVoice ->
                    onValueChange(setting.copy(voice = newVoice))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = voiceExpanded,
                onDismissRequest = { voiceExpanded = false }
            ) {
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice) },
                        onClick = {
                            voiceExpanded = false
                            onValueChange(setting.copy(voice = voice))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MiMoTTSConfiguration(
    setting: TTSProviderSetting.MiMo,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // MiMo 配置均为自由输入 默认值只是占位
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("mimo-xxx") },
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.xiaomimimo.com/v1") }
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("mimo-v2-tts") }
        )
    }

    // Voice
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_description)) }
    ) {
        OutlinedTextField(
            value = setting.voice,
            onValueChange = { newVoice ->
                onValueChange(setting.copy(voice = newVoice))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("mimo_default") }
        )
    }
}

@Composable
private fun MiniMaxTTSConfiguration(
    setting: TTSProviderSetting.MiniMax,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("speech-2.5-hd-preview") }
        )
    }

    // Voice ID
    var voiceIdExpanded by remember { mutableStateOf(false) }
    val voiceIds = listOf(
        "male-qn-qingse",
        "male-qn-jingying",
        "male-qn-badao",
        "male-qn-daxuesheng",
        "female-shaonv",
        "female-yujie",
        "female-chengshu",
        "female-tianmei",
        "audiobook_male_1",
        "audiobook_female_1",
        "cartoon_pig"
    )

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice_id)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_id_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = voiceIdExpanded,
            onExpandedChange = { voiceIdExpanded = !voiceIdExpanded }
        ) {
            OutlinedTextField(
                value = setting.voiceId,
                onValueChange = { newVoiceId ->
                    onValueChange(setting.copy(voiceId = newVoiceId))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceIdExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = voiceIdExpanded,
                onDismissRequest = { voiceIdExpanded = false }
            ) {
                voiceIds.forEach { voiceId ->
                    DropdownMenuItem(
                        text = { Text(voiceId) },
                        onClick = {
                            voiceIdExpanded = false
                            onValueChange(setting.copy(voiceId = voiceId))
                        }
                    )
                }
            }
        }
    }

    // Speed
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_speed)) },
        description = { Text(stringResource(R.string.setting_tts_page_speed_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.speed,
            onValueChange = { newSpeed ->
                if (newSpeed in 0.25f..4.0f) {
                    onValueChange(setting.copy(speed = newSpeed))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_speed)
        )
    }
}

@Composable
private fun GeminiTTSConfiguration(
    setting: TTSProviderSetting.Gemini,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_api_key_placeholder_gemini)) },
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_model_placeholder_gemini)) }
        )
    }

    // Voice Name
    var voiceExpanded by remember { mutableStateOf(false) }
    val voices = listOf(
        "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede",
        "Callirrhoe", "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba",
        "Despina", "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar",
        "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi",
        "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat"
    )

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice_name)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_name_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = voiceExpanded,
            onExpandedChange = { voiceExpanded = !voiceExpanded }
        ) {
            OutlinedTextField(
                value = setting.voiceName,
                onValueChange = { newVoiceName ->
                    onValueChange(setting.copy(voiceName = newVoiceName))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = voiceExpanded,
                onDismissRequest = { voiceExpanded = false }
            ) {
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice) },
                        onClick = {
                            voiceExpanded = false
                            onValueChange(setting.copy(voiceName = voice))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemTTSConfiguration(
    setting: TTSProviderSetting.SystemTTS,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // Speech Rate
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_speech_rate)) },
        description = { Text(stringResource(R.string.setting_tts_page_speech_rate_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.speechRate,
            onValueChange = { newRate ->
                if (newRate in 0.1f..3.0f) {
                    onValueChange(setting.copy(speechRate = newRate))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_speech_rate)
        )
    }

    // Pitch
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_pitch)) },
        description = { Text(stringResource(R.string.setting_tts_page_pitch_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.pitch,
            onValueChange = { newPitch ->
                if (newPitch in 0.1f..2.0f) {
                    onValueChange(setting.copy(pitch = newPitch))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_pitch)
        )
    }
}

@Composable
private fun QwenTTSConfiguration(
    setting: TTSProviderSetting.Qwen,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-xxx") },
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("qwen3-tts-flash") }
        )
    }

    // Voice
    var voiceExpanded by remember { mutableStateOf(false) }
    val voices = listOf(
        "Cherry", "Serene", "Ethan", "Chelsie",
        "Momo", "Vivian", "Moon", "Maia", "Kai",
        "Nofish", "Bella", "Jennifer", "Ryan",
        "Katerina", "Aiden", "Eldric Sage", "Mia",
        "Mochi", "Bellona", "Vincent", "Bunny",
        "Neil", "Elias", "Arthur", "Nini"
    )

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = voiceExpanded,
            onExpandedChange = { voiceExpanded = !voiceExpanded }
        ) {
            OutlinedTextField(
                value = setting.voice,
                onValueChange = { newVoice ->
                    onValueChange(setting.copy(voice = newVoice))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = voiceExpanded,
                onDismissRequest = { voiceExpanded = false }
            ) {
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice) },
                        onClick = {
                            voiceExpanded = false
                            onValueChange(setting.copy(voice = voice))
                        }
                    )
                }
            }
        }
    }

    // Language Type
    var languageExpanded by remember { mutableStateOf(false) }
    val languageTypes = listOf("Auto", "Chinese", "English", "Japanese", "Korean")

    FormItem(
        label = { Text("Language Type") },
        description = { Text("Language type for TTS synthesis") }
    ) {
        ExposedDropdownMenuBox(
            expanded = languageExpanded,
            onExpandedChange = { languageExpanded = !languageExpanded }
        ) {
            OutlinedTextField(
                value = setting.languageType,
                onValueChange = { newLanguageType ->
                    onValueChange(setting.copy(languageType = newLanguageType))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = languageExpanded,
                onDismissRequest = { languageExpanded = false }
            ) {
                languageTypes.forEach { languageType ->
                    DropdownMenuItem(
                        text = { Text(languageType) },
                        onClick = {
                            languageExpanded = false
                            onValueChange(setting.copy(languageType = languageType))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroqTTSConfiguration(
    setting: TTSProviderSetting.Groq,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("gsk_xxx") },
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("canopylabs/orpheus-v1-english") }
        )
    }

    // Voice (canopylabs/orpheus-v1-english: autumn, diana, hannah, austin, daniel, troy)
    // Voice (canopylabs/orpheus-arabic-saudi: abdullah, fahad, sultan, lulwa, noura, aisha)
    var voiceExpanded by remember { mutableStateOf(false) }
    val voices = listOf(
        "austin",
        "autumn",
        "daniel",
        "diana",
        "hannah",
        "troy",
        "abdullah",
        "fahad",
        "sultan",
        "lulwa",
        "noura",
        "aisha"
    )

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = voiceExpanded,
            onExpandedChange = { voiceExpanded = !voiceExpanded }
        ) {
            OutlinedTextField(
                value = setting.voice,
                onValueChange = { newVoice ->
                    onValueChange(setting.copy(voice = newVoice))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = voiceExpanded,
                onDismissRequest = { voiceExpanded = false }
            ) {
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice) },
                        onClick = {
                            voiceExpanded = false
                            onValueChange(setting.copy(voice = voice))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun XAITTSConfiguration(
    setting: TTSProviderSetting.XAI,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("xai-xxx") },
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.x.ai/v1") }
        )
    }

    // Voice ID
    var voiceExpanded by remember { mutableStateOf(false) }
    val voices = listOf(
        "altair" to "Altair",
        "ara" to "Ara",
        "atlas" to "Atlas",
        "carina" to "Carina",
        "castor" to "Castor",
        "celeste" to "Celeste",
        "cosmo" to "Cosmo",
        "eve" to "Eve",
        "helios" to "Helios",
        "helix" to "Helix",
        "iris" to "Iris",
        "kepler" to "Kepler",
        "leo" to "Leo",
        "lumen" to "Lumen",
        "luna" to "Luna",
        "lux" to "Lux",
        "naksh" to "Naksh",
        "orion" to "Orion",
        "perseus" to "Perseus",
        "rex" to "Rex",
        "rigel" to "Rigel",
        "sal" to "Sal",
        "sirius" to "Sirius",
        "ursa" to "Ursa",
        "zenith" to "Zenith",
        "zagan" to "Zagan"
    )

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = voiceExpanded,
            onExpandedChange = { voiceExpanded = !voiceExpanded }
        ) {
            OutlinedTextField(
                value = setting.voiceId,
                onValueChange = { newVoiceId ->
                    onValueChange(setting.copy(voiceId = newVoiceId))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = voiceExpanded,
                onDismissRequest = { voiceExpanded = false }
            ) {
                voices.forEach { (voiceId, description) ->
                    DropdownMenuItem(
                        text = { Text(description) },
                        onClick = {
                            voiceExpanded = false
                            onValueChange(setting.copy(voiceId = voiceId))
                        }
                    )
                }
            }
        }
    }

    // Language
    var languageExpanded by remember { mutableStateOf(false) }
    val languages = listOf(
        "auto" to "Auto-detect",
        "en" to "English",
        "zh" to "Chinese (Simplified)",
        "ja" to "Japanese",
        "ko" to "Korean",
        "fr" to "French",
        "de" to "German",
        "es-ES" to "Spanish (Spain)",
        "es-MX" to "Spanish (Mexico)",
        "pt-BR" to "Portuguese (Brazil)",
        "pt-PT" to "Portuguese (Portugal)",
        "it" to "Italian",
        "ru" to "Russian",
        "ar-EG" to "Arabic (Egypt)",
        "hi" to "Hindi",
        "tr" to "Turkish",
        "vi" to "Vietnamese",
        "id" to "Indonesian",
        "bn" to "Bengali"
    )

    FormItem(
        label = { Text("Language") },
    ) {
        ExposedDropdownMenuBox(
            expanded = languageExpanded,
            onExpandedChange = { languageExpanded = !languageExpanded }
        ) {
            OutlinedTextField(
                value = setting.language,
                onValueChange = { newLanguage ->
                    onValueChange(setting.copy(language = newLanguage))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = languageExpanded,
                onDismissRequest = { languageExpanded = false }
            ) {
                languages.forEach { (code, displayName) ->
                    DropdownMenuItem(
                        text = { Text("$displayName ($code)") },
                        onClick = {
                            languageExpanded = false
                            onValueChange(setting.copy(language = code))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ElevenLabsTTSConfiguration(
    setting: TTSProviderSetting.ElevenLabs,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk_...") },
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.elevenlabs.io") }
        )
    }

    // Model
    var modelExpanded by remember { mutableStateOf(false) }
    val models = listOf(
        "eleven_multilingual_v2" to "Eleven Multilingual v2",
        "eleven_v3" to "Eleven v3",
        "eleven_flash_v2_5" to "Eleven Flash v2.5"
    )

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = modelExpanded,
            onExpandedChange = { modelExpanded = !modelExpanded }
        ) {
            OutlinedTextField(
                value = setting.model,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(model = newModel))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = modelExpanded,
                onDismissRequest = { modelExpanded = false }
            ) {
                models.forEach { (modelId, displayName) ->
                    DropdownMenuItem(
                        text = { Text("$displayName ($modelId)") },
                        onClick = {
                            modelExpanded = false
                            onValueChange(setting.copy(model = modelId))
                        }
                    )
                }
            }
        }
    }

    // Voice ID
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_description)) }
    ) {
        OutlinedTextField(
            value = setting.voiceId,
            onValueChange = { newVoiceId ->
                onValueChange(setting.copy(voiceId = newVoiceId))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("JBFqnCBsd6RMkjVDRZzb") }
        )
    }

    // Stability
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_stability)) },
        description = { Text(stringResource(R.string.setting_tts_page_stability_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.stability,
            onValueChange = { newStability ->
                onValueChange(setting.copy(stability = newStability.coerceIn(0f, 1f)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = "0.5",
        )
    }

    // Similarity Boost
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_similarity_boost)) },
        description = { Text(stringResource(R.string.setting_tts_page_similarity_boost_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.similarityBoost,
            onValueChange = { newSimilarityBoost ->
                onValueChange(setting.copy(similarityBoost = newSimilarityBoost.coerceIn(0f, 1f)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = "0.75",
        )
    }
}

@Composable
private fun FishAudioTTSConfiguration(
    setting: TTSProviderSetting.FishAudio,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://fish.audio/app/api-keys") },
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.fish.audio") }
        )
    }

    // Model (下拉选择框 + 文本输入框，完全同 ElevenLabs 格式)
    var modelExpanded by remember { mutableStateOf(false) }
    val models = listOf(
        "s2.1-pro" to "S2.1-Pro (推荐)",
        "s2.1-pro-free" to "S2.1-Pro Free (免费)",
        "s2-pro" to "S2-Pro",
        "s1" to "S1"
    )

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = modelExpanded,
            onExpandedChange = { modelExpanded = !modelExpanded }
        ) {
            OutlinedTextField(
                value = setting.model,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(model = newModel))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = modelExpanded,
                onDismissRequest = { modelExpanded = false }
            ) {
                models.forEach { (modelId, displayName) ->
                    DropdownMenuItem(
                        text = { Text("$displayName ($modelId)") },
                        onClick = {
                            modelExpanded = false
                            onValueChange(setting.copy(model = modelId))
                        }
                    )
                }
            }
        }
    }

    // Voice ID (reference_id)
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice_id)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_id_description)) }
    ) {
        OutlinedTextField(
            value = setting.referenceId,
            onValueChange = { newReferenceId ->
                onValueChange(setting.copy(referenceId = newReferenceId))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("802e3bc2b27e49c2995d23ef70e6ac89") }
        )
    }

    // Temperature
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_temperature)) },
        description = { Text(stringResource(R.string.setting_tts_page_temperature_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.temperature,
            onValueChange = { newTemperature ->
                onValueChange(setting.copy(temperature = newTemperature.coerceIn(0f, 1f)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = "0.7",
        )
    }

    // Speed
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_speed)) },
        description = { Text(stringResource(R.string.setting_tts_page_fish_audio_speed_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.speed,
            onValueChange = { newSpeed ->
                onValueChange(setting.copy(speed = newSpeed.coerceIn(0.5f, 2f)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = "1.0",
        )
    }
}

@Composable
private fun StepTTSConfiguration(
    setting: TTSProviderSetting.Step,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text("Get API key from StepFun: platform.stepfun.com/interface-key") }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Get API key from StepFun") },
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.stepfun.com") }
        )
    }

    // Model
    var modelExpanded by remember { mutableStateOf(false) }
    val models = listOf(
        "step-tts-mini" to "step-tts-mini (轻量, 便宜)",
        "step-tts-vivid" to "step-tts-vivid (情感丰富)",
        "stepaudio-2.5-tts" to "stepaudio-2.5-tts (语境感知, 支持 instruction)",
        "step-tts-2" to "step-tts-2 (上一代)",
    )

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = modelExpanded,
            onExpandedChange = { modelExpanded = !modelExpanded }
        ) {
            OutlinedTextField(
                value = setting.model,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(model = newModel))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = modelExpanded,
                onDismissRequest = { modelExpanded = false }
            ) {
                models.forEach { (modelId, description) ->
                    DropdownMenuItem(
                        text = { Text(description) },
                        onClick = {
                            modelExpanded = false
                            onValueChange(setting.copy(model = modelId))
                        }
                    )
                }
            }
        }
    }

    // Voice
    var voiceExpanded by remember { mutableStateOf(false) }
    // 部分常用 voice-id, 完整列表见官方开发指南
    // https://platform.stepfun.com/docs/zh/guides/developer/tts
    val voices = listOf(
        "elegantgentle-female" to "气质温婉 (elegantgentle-female)",
        "livelybreezy-female" to "活力轻快 (livelybreezy-female)",
        "energeticconfident-female" to "活力自信 (energeticconfident-female)",
        "jingdiannvsheng" to "经典女声 (jingdiannvsheng)",
        "wenroushunv" to "温柔熟女 (wenroushunv)",
        "tianmeinvsheng" to "甜美女声 (tianmeinvsheng)",
        "qingchunshaonv" to "清纯少女 (qingchunshaonv)",
        "wenrounvsheng" to "温柔女声 (wenrounvsheng)",
        "ruanmengnvsheng" to "软萌女生 (ruanmengnvsheng)",
        "youyanvsheng" to "优雅女生 (youyanvsheng)",
        "lengyanyujie" to "冷艳御姐 (lengyanyujie)",
        "shuangkuaijiejie" to "爽快姐姐 (shuangkuaijiejie)",
        "wenjingxuejie" to "文静学姐 (wenjingxuejie)",
        "linjiajiejie" to "邻家姐姐 (linjiajiejie)",
        "linjiameimei" to "邻家妹妹 (linjiameimei)",
        "zhixingjiejie" to "知性姐姐 (zhixingjiejie)",
        "cixingnansheng" to "磁性男声 (cixingnansheng)",
        "wenrounansheng" to "温柔男声 (wenrounansheng)",
        "yuanqinansheng" to "元气男声 (yuanqinansheng)",
        "zhengpaiqingnian" to "正派青年 (zhengpaiqingnian)",
        "ruyananshi" to "儒雅男士 (ruyananshi)",
        "boyinnansheng" to "播音男声 (boyinnansheng)",
        "shenchennanyin" to "深沉男音 (shenchennanyin)",
        "shuangkuainansheng" to "爽快男声 (shuangkuainansheng)",
        "ganliannvsheng" to "干练女声 (ganliannvsheng)",
        "qinhenvsheng" to "亲切女声 (qinhenvsheng)",
        "huolinvsheng" to "活力女声 (huolinvsheng)",
        "jilingshaonv" to "机灵少女 (jilingshaonv)",
        "yuanqishaonv" to "元气少女 (yuanqishaonv)",
        "wenrougongzi" to "温柔公子 (wenrougongzi)",
        "qingniandaxuesheng" to "青年大学生 (qingniandaxuesheng)",
    )

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = voiceExpanded,
            onExpandedChange = { voiceExpanded = !voiceExpanded }
        ) {
            OutlinedTextField(
                value = setting.voice,
                onValueChange = { newVoice ->
                    onValueChange(setting.copy(voice = newVoice))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = voiceExpanded,
                onDismissRequest = { voiceExpanded = false }
            ) {
                voices.forEach { (voiceId, description) ->
                    DropdownMenuItem(
                        text = { Text(description) },
                        onClick = {
                            voiceExpanded = false
                            onValueChange(setting.copy(voice = voiceId))
                        }
                    )
                }
            }
        }
    }

    // Response Format
    var formatExpanded by remember { mutableStateOf(false) }
    val formats = listOf("mp3", "wav", "pcm", "opus", "flac")

    FormItem(
        label = { Text("Response Format") },
        description = { Text("Audio encoding format (note: StepFun API field is camelCase)") }
    ) {
        ExposedDropdownMenuBox(
            expanded = formatExpanded,
            onExpandedChange = { formatExpanded = !formatExpanded }
        ) {
            OutlinedTextField(
                value = setting.responseFormat,
                onValueChange = { newFormat ->
                    onValueChange(setting.copy(responseFormat = newFormat))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = formatExpanded,
                onDismissRequest = { formatExpanded = false }
            ) {
                formats.forEach { format ->
                    DropdownMenuItem(
                        text = { Text(format) },
                        onClick = {
                            formatExpanded = false
                            onValueChange(setting.copy(responseFormat = format))
                        }
                    )
                }
            }
        }
    }

    // Speed
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_speed)) },
        description = { Text("Speed (0.5 - 2.0, 1.0 is normal)") }
    ) {
        OutlinedNumberInput(
            value = setting.speed,
            onValueChange = { newSpeed ->
                if (newSpeed in 0.5f..2.0f) {
                    onValueChange(setting.copy(speed = newSpeed))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_speed)
        )
    }

    // Volume
    FormItem(
        label = { Text("Volume") },
        description = { Text("Volume (0.1 - 2.0, 1.0 is normal)") }
    ) {
        OutlinedNumberInput(
            value = setting.volume,
            onValueChange = { newVolume ->
                if (newVolume in 0.1f..2.0f) {
                    onValueChange(setting.copy(volume = newVolume))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Volume"
        )
    }

    // Sample Rate
    var sampleRateExpanded by remember { mutableStateOf(false) }
    val sampleRates = listOf(8000, 16000, 22050, 24000)

    FormItem(
        label = { Text("Sample Rate") },
        description = { Text("Sampling rate (Hz)") }
    ) {
        ExposedDropdownMenuBox(
            expanded = sampleRateExpanded,
            onExpandedChange = { sampleRateExpanded = !sampleRateExpanded }
        ) {
            OutlinedTextField(
                value = setting.sampleRate.toString(),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = sampleRateExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = sampleRateExpanded,
                onDismissRequest = { sampleRateExpanded = false }
            ) {
                sampleRates.forEach { rate ->
                    DropdownMenuItem(
                        text = { Text("$rate Hz") },
                        onClick = {
                            sampleRateExpanded = false
                            onValueChange(setting.copy(sampleRate = rate))
                        }
                    )
                }
            }
        }
    }

    // Instruction (仅 stepaudio-2.5-tts 生效)
    FormItem(
        label = { Text("Instruction") },
        description = { Text("Global context instruction, only applies to stepaudio-2.5-tts (≤200 chars, leave empty to skip)") }
    ) {
        OutlinedTextField(
            value = setting.instruction,
            onValueChange = { newInstruction ->
                // 服务端上限 200 字符, 客户端做一层保护
                if (newInstruction.length <= 200) {
                    onValueChange(setting.copy(instruction = newInstruction))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. gentle tone, slower pace") },
            minLines = 2,
            maxLines = 4,
        )
    }
}

@Composable
private fun NekoSpeakTTSConfiguration(
    setting: TTSProviderSetting.NekoSpeakTts,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val dest = copyModelToAppDir(context, uri, "neko-speak")
            if (dest != null) {
                onValueChange(setting.copy(modelPath = dest.absolutePath))
            }
        }
    }

    // Model Path
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text("Model file path") }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = setting.modelPath,
                onValueChange = { newPath ->
                    onValueChange(setting.copy(modelPath = newPath))
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("/data/local/tmp/tts_model.ort") },
            )
            Button(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                Text("Browse")
            }
        }
    }

    // Voice
    FormItem(
        label = { Text("Voice") },
        description = { Text("Voice/timbre to use") }
    ) {
        OutlinedTextField(
            value = setting.voice,
            onValueChange = { newVoice ->
                onValueChange(setting.copy(voice = newVoice))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("default") },
        )
    }

    // Speed
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_speech_rate)) },
        description = { Text(stringResource(R.string.setting_tts_page_speech_rate_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.speed,
            onValueChange = { newSpeed ->
                if (newSpeed in 0.1f..3.0f) {
                    onValueChange(setting.copy(speed = newSpeed))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Speed"
        )
    }

    // Pitch
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_pitch)) },
        description = { Text(stringResource(R.string.setting_tts_page_pitch_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.pitch,
            onValueChange = { newPitch ->
                if (newPitch in 0.1f..2.0f) {
                    onValueChange(setting.copy(pitch = newPitch))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Pitch"
        )
    }
}

@Composable
private fun PocketTTSConfiguration(
    setting: TTSProviderSetting.PocketTts,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val httpClient = koinInjectOkHttp()
    val toaster = LocalToaster.current
    val doneTemplate = stringResource(R.string.local_tts_download_done)
    val errorTemplate = stringResource(R.string.local_tts_download_error)

    // States
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var doneMsg by remember { mutableStateOf<String?>(null) }

    // Folder picker (whole bundle) — OpenDocumentTree to select a directory.
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                LocalTtsModelManager.importBundleFromTree(
                    context, "pocket-tts", uri, PocketTtsBundle.requiredFiles,
                )
            }.onSuccess { dest ->
                onValueChange(setting.copy(modelPath = dest.absolutePath))
                toaster.show(
                    doneTemplate.format(dest.absolutePath),
                    type = ToastType.Success,
                )
            }.onFailure { e ->
                toaster.show(e.message ?: "Import failed", type = ToastType.Error)
            }
        }
    }

    // Model directory
    FormItem(
        label = { Text(stringResource(R.string.local_tts_model_dir_label)) },
        description = { Text(stringResource(R.string.local_tts_model_dir_desc)) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = setting.modelPath,
                onValueChange = { onValueChange(setting.copy(modelPath = it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.local_tts_model_dir_placeholder_pocket)) },
            )
            Button(onClick = { folderPicker.launch(null) }) {
                Text(stringResource(R.string.local_tts_browse))
            }
        }
    }

    // Download section
    FormItem(
        label = { Text(stringResource(R.string.local_tts_download_section)) },
        description = { Text(stringResource(R.string.local_tts_download_desc)) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val entry = PocketTtsCatalog.ENTRIES.first()
            val installed = LocalTtsModelManager
                .missingFiles(
                    File(setting.modelPath),
                    PocketTtsBundle.requiredFiles
                ).isEmpty() && setting.modelPath.isNotBlank()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (installed) {
                    Text(
                        text = stringResource(R.string.local_tts_installed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                downloading = true
                                error = null; doneMsg = null
                                runCatching {
                                    val urls = PocketTtsBundle.requiredFiles.map { file ->
                                        entry.resolveFileUrl(file) to file
                                    }
                                    LocalTtsModelManager.downloadBundle(
                                        context, httpClient, "pocket-tts", urls,
                                        onProgress = { progress = it },
                                    )
                                }.onSuccess { dest ->
                                    onValueChange(setting.copy(modelPath = dest.absolutePath))
                                    doneMsg = dest.absolutePath
                                    toaster.show(
                                        doneTemplate.format(dest.absolutePath),
                                        type = ToastType.Success,
                                    )
                                }.onFailure { e ->
                                    error = e.message ?: "Download failed"
                                    toaster.show(
                                        errorTemplate.format(error!!),
                                        type = ToastType.Error,
                                    )
                                }
                                downloading = false
                            }
                        },
                        enabled = !downloading,
                    ) { Text(stringResource(R.string.local_tts_install)) }
                }
                OutlinedButton(onClick = { openModelSourceUrl(context, entry.sourceUrl) }) {
                    Text(stringResource(R.string.local_tts_source))
                }
            }

            // Paste link
            var manualUrl by remember { mutableStateOf(setting.hfLink) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    label = { Text(stringResource(R.string.local_tts_paste_link)) },
                    placeholder = { Text(stringResource(R.string.local_tts_paste_link_hint)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = {
                        val url = manualUrl.trim()
                        if (url.isBlank()) return@OutlinedButton
                        scope.launch {
                            downloading = true
                            error = null; doneMsg = null
                            runCatching {
                                val normalized = ModelInstall.normalizeHuggingFaceUrl(url)
                                if (!ModelInstall.isValidDownloadUrl(normalized)) {
                                    throw IllegalArgumentException("Invalid HuggingFace URL")
                                }
                                val urls = PocketTtsBundle.requiredFiles.map { file ->
                                    normalized.trimEnd('/') + "/resolve/main/" + file to file
                                }
                                LocalTtsModelManager.downloadBundle(
                                    context, httpClient, "pocket-tts", urls,
                                    onProgress = { progress = it },
                                )
                            }.onSuccess { dest ->
                                onValueChange(setting.copy(modelPath = dest.absolutePath, hfLink = manualUrl))
                                toaster.show(
                                    doneTemplate.format(dest.absolutePath),
                                    type = ToastType.Success,
                                )
                            }.onFailure { e ->
                                error = e.message ?: "Download failed"
                                toaster.show(
                                    errorTemplate.format(error!!),
                                    type = ToastType.Error,
                                )
                            }
                            downloading = false
                        }
                    },
                    enabled = manualUrl.isNotBlank() && !downloading,
                ) { Text(stringResource(R.string.local_tts_download_paste)) }
            }
        }
    }

    // Progress / status
    if (downloading) {
        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.local_tts_download_progress, progress),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    error?.let { msg ->
        Text(
            text = stringResource(R.string.local_tts_download_error, msg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    // --- Full synthesis settings ---
    FormItem(
        label = { Text(stringResource(R.string.local_tts_flow_steps)) },
        description = { Text(stringResource(R.string.local_tts_flow_steps_desc)) }
    ) {
        OutlinedTextField(
            value = setting.flowSteps.toString(),
            onValueChange = { text ->
                text.toIntOrNull()?.takeIf { it in 1..32 }?.let {
                    onValueChange(setting.copy(flowSteps = it))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("4") },
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.local_tts_temperature)) },
        description = { Text(stringResource(R.string.local_tts_temperature_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.temperature,
            onValueChange = { v ->
                if (v.isFinite() && v in 0f..10f) onValueChange(setting.copy(temperature = v))
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Temperature",
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.local_tts_max_frames)) },
        description = { Text(stringResource(R.string.local_tts_max_frames_desc)) }
    ) {
        OutlinedTextField(
            value = setting.maxFrames.toString(),
            onValueChange = { text ->
                text.toIntOrNull()?.takeIf { it in 1..1000 }?.let {
                    onValueChange(setting.copy(maxFrames = it))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("1000") },
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.local_tts_frames_after_eos)) },
        description = { Text(stringResource(R.string.local_tts_frames_after_eos_desc)) }
    ) {
        OutlinedTextField(
            value = setting.framesAfterEos.toString(),
            onValueChange = { text ->
                text.toIntOrNull()?.takeIf { it in 0..50 }?.let {
                    onValueChange(setting.copy(framesAfterEos = it))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("0") },
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.local_tts_eos_threshold)) },
        description = { Text(stringResource(R.string.local_tts_eos_threshold_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.eosThreshold,
            onValueChange = { v ->
                if (v.isFinite()) onValueChange(setting.copy(eosThreshold = v))
            },
            modifier = Modifier.fillMaxWidth(),
            label = "EOS threshold",
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.local_tts_intra_threads)) },
        description = { Text(stringResource(R.string.local_tts_intra_threads_desc)) }
    ) {
        OutlinedTextField(
            value = setting.intraThreads.toString(),
            onValueChange = { text ->
                text.toIntOrNull()?.takeIf { it in 1..64 }?.let {
                    onValueChange(setting.copy(intraThreads = it))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("4") },
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.local_tts_seed)) },
        description = { Text(stringResource(R.string.local_tts_seed_desc)) }
    ) {
        OutlinedTextField(
            value = setting.seed.toString(),
            onValueChange = { text ->
                text.toLongOrNull()?.let { onValueChange(setting.copy(seed = it)) }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("-1") },
        )
    }
}

@Composable
private fun KittenTTSConfiguration(
    setting: TTSProviderSetting.KittenTts,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val httpClient = koinInjectOkHttp()
    val toaster = LocalToaster.current
    val doneTemplate = stringResource(R.string.local_tts_download_done)
    val errorTemplate = stringResource(R.string.local_tts_download_error)

    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                LocalTtsModelManager.importBundleFromTree(
                    context, "kitten-tts", uri, KittenTtsBundle.requiredFiles,
                )
            }.onSuccess { dest ->
                onValueChange(setting.copy(modelPath = dest.absolutePath))
                toaster.show(
                    doneTemplate.format(dest.absolutePath),
                    type = ToastType.Success,
                )
            }.onFailure { e ->
                toaster.show(e.message ?: "Import failed", type = ToastType.Error)
            }
        }
    }

    FormItem(
        label = { Text(stringResource(R.string.local_tts_model_dir_label)) },
        description = { Text(stringResource(R.string.local_tts_model_dir_desc)) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = setting.modelPath,
                onValueChange = { onValueChange(setting.copy(modelPath = it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.local_tts_model_dir_placeholder_kitten)) },
            )
            Button(onClick = { folderPicker.launch(null) }) {
                Text(stringResource(R.string.local_tts_browse))
            }
        }
    }

    FormItem(
        label = { Text(stringResource(R.string.local_tts_download_section)) },
        description = { Text(stringResource(R.string.local_tts_download_desc)) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val entry = KittenTtsCatalog.ENTRIES.first()
            val installed = LocalTtsModelManager
                .missingFiles(File(setting.modelPath), entry.requiredFiles)
                .isEmpty() && setting.modelPath.isNotBlank()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (installed) {
                    Text(
                        text = stringResource(R.string.local_tts_installed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                downloading = true
                                error = null
                                runCatching {
                                    val urls = entry.requiredFiles.map { file ->
                                        entry.resolveFileUrl(file) to file
                                    }
                                    LocalTtsModelManager.downloadBundle(
                                        context, httpClient, "kitten-tts", urls,
                                        onProgress = { progress = it },
                                    )
                            }.onSuccess { dest ->
                                onValueChange(setting.copy(modelPath = dest.absolutePath, hfLink = entry.sourceUrl))
                                toaster.show(
                                    doneTemplate.format(dest.absolutePath),
                                    type = ToastType.Success,
                                )
                            }.onFailure { e ->
                                error = e.message ?: "Download failed"
                                toaster.show(
                                    errorTemplate.format(error!!),
                                    type = ToastType.Error,
                                )
                            }
                                downloading = false
                            }
                        },
                        enabled = !downloading,
                    ) { Text(stringResource(R.string.local_tts_install)) }
                }
                OutlinedButton(onClick = { openModelSourceUrl(context, entry.sourceUrl) }) {
                    Text(stringResource(R.string.local_tts_source))
                }
            }

            var manualUrl by remember { mutableStateOf(setting.hfLink) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    label = { Text(stringResource(R.string.local_tts_paste_link)) },
                    placeholder = { Text(stringResource(R.string.local_tts_paste_link_hint)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = {
                        val url = manualUrl.trim()
                        if (url.isBlank()) return@OutlinedButton
                        scope.launch {
                            downloading = true
                            error = null
                            runCatching {
                                val normalized = ModelInstall.normalizeHuggingFaceUrl(url)
                                if (!ModelInstall.isValidDownloadUrl(normalized)) {
                                    throw IllegalArgumentException("Invalid HuggingFace URL")
                                }
                                val urls = entry.requiredFiles.map { file ->
                                    normalized.trimEnd('/') + "/resolve/main/" + file to file
                                }
                                LocalTtsModelManager.downloadBundle(
                                    context, httpClient, "kitten-tts", urls,
                                    onProgress = { progress = it },
                                )
                            }.onSuccess { dest ->
                                onValueChange(setting.copy(modelPath = dest.absolutePath, hfLink = manualUrl))
                                toaster.show(
                                    doneTemplate.format(dest.absolutePath),
                                    type = ToastType.Success,
                                )
                            }.onFailure { e ->
                                error = e.message ?: "Download failed"
                                toaster.show(
                                    errorTemplate.format(error!!),
                                    type = ToastType.Error,
                                )
                            }
                            downloading = false
                        }
                    },
                    enabled = manualUrl.isNotBlank() && !downloading,
                ) { Text(stringResource(R.string.local_tts_download_paste)) }
            }
        }
    }

    if (downloading) {
        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.local_tts_download_progress, progress),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    error?.let { msg ->
        Text(
            text = stringResource(R.string.local_tts_download_error, msg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    // Voice selector
    var voiceExpanded by remember { mutableStateOf(false) }
    FormItem(
        label = { Text(stringResource(R.string.local_tts_voice)) },
        description = { Text(stringResource(R.string.local_tts_voice_desc)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = voiceExpanded,
            onExpandedChange = { voiceExpanded = !voiceExpanded },
        ) {
            OutlinedTextField(
                value = KittenTtsConfig.VOICE_LABELS[setting.voice] ?: setting.voice,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
            )
            ExposedDropdownMenu(expanded = voiceExpanded, onDismissRequest = { voiceExpanded = false }) {
                KittenTtsConfig.AVAILABLE_VOICES.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(KittenTtsConfig.VOICE_LABELS[voice] ?: voice) },
                        onClick = {
                            voiceExpanded = false
                            onValueChange(setting.copy(voice = voice))
                        },
                    )
                }
            }
        }
    }

    // Speed
    FormItem(
        label = { Text(stringResource(R.string.local_tts_speed)) },
        description = { Text(stringResource(R.string.local_tts_speed_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.speed,
            onValueChange = { v ->
                if (v.isFinite() && v in 0.25f..4.0f) onValueChange(setting.copy(speed = v))
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Speed",
        )
    }
}

@Composable
private fun Qwen3TTSConfiguration(
    setting: TTSProviderSetting.Qwen3Tts,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val httpClient = koinInjectOkHttp()
    val toaster = LocalToaster.current
    val doneTemplate = stringResource(R.string.local_tts_download_done)
    val errorTemplate = stringResource(R.string.local_tts_download_error)

    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    FormItem(
        label = { Text(stringResource(R.string.local_tts_model_dir_label)) },
        description = { Text(stringResource(R.string.local_tts_model_dir_desc)) }
    ) {
        OutlinedTextField(
            value = setting.modelPath,
            onValueChange = { onValueChange(setting.copy(modelPath = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.local_tts_model_dir_placeholder_qwen3)) },
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.local_tts_download_section)) },
        description = { Text(stringResource(R.string.local_tts_download_desc)) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val entry = Qwen3TtsCatalog.ENTRIES.first()
            val installed = LocalTtsModelManager
                .missingFiles(File(setting.modelPath), entry.requiredFiles)
                .isEmpty() && setting.modelPath.isNotBlank()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (installed) {
                    Text(
                        text = stringResource(R.string.local_tts_installed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                downloading = true
                                error = null
                                runCatching {
                                    LocalTtsModelManager.downloadBundle(
                                        context, httpClient, "qwen3-tts", entry.downloadPairs(),
                                        onProgress = { progress = it },
                                    )
                                }.onSuccess { dest ->
                                    onValueChange(setting.copy(modelPath = dest.absolutePath, hfLink = entry.sourceUrl))
                                    toaster.show(
                                        doneTemplate.format(dest.absolutePath),
                                        type = ToastType.Success,
                                    )
                                }.onFailure { e ->
                                    error = e.message ?: "Download failed"
                                    toaster.show(
                                        errorTemplate.format(error!!),
                                        type = ToastType.Error,
                                    )
                                }
                                downloading = false
                            }
                        },
                        enabled = !downloading,
                    ) { Text(stringResource(R.string.local_tts_install)) }
                }
                OutlinedButton(onClick = { openModelSourceUrl(context, entry.sourceUrl) }) {
                    Text(stringResource(R.string.local_tts_source))
                }
            }

            var manualUrl by remember { mutableStateOf(setting.hfLink) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    label = { Text(stringResource(R.string.local_tts_paste_link)) },
                    placeholder = { Text(stringResource(R.string.local_tts_paste_link_hint)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = {
                        val url = manualUrl.trim()
                        if (url.isBlank()) return@OutlinedButton
                        scope.launch {
                            downloading = true
                            error = null
                            runCatching {
                                val normalized = ModelInstall.normalizeHuggingFaceUrl(url)
                                if (!ModelInstall.isValidDownloadUrl(normalized)) {
                                    throw IllegalArgumentException("Invalid HuggingFace URL")
                                }
                                val pairs = entry.requiredFiles.mapNotNull { file ->
                                    val remote = entry.remoteForLocal(file) ?: return@mapNotNull null
                                    normalized.trimEnd('/') + "/resolve/main/" + remote to file
                                }
                                LocalTtsModelManager.downloadBundle(
                                    context, httpClient, "qwen3-tts", pairs,
                                    onProgress = { progress = it },
                                )
                            }.onSuccess { dest ->
                                onValueChange(setting.copy(modelPath = dest.absolutePath, hfLink = manualUrl))
                                toaster.show(
                                    doneTemplate.format(dest.absolutePath),
                                    type = ToastType.Success,
                                )
                            }.onFailure { e ->
                                error = e.message ?: "Download failed"
                                toaster.show(
                                    errorTemplate.format(error!!),
                                    type = ToastType.Error,
                                )
                            }
                            downloading = false
                        }
                    },
                    enabled = manualUrl.isNotBlank() && !downloading,
                ) { Text(stringResource(R.string.local_tts_download_paste)) }
            }
        }
    }

    if (downloading) {
        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.local_tts_download_progress, progress),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    error?.let { msg ->
        Text(
            text = stringResource(R.string.local_tts_download_error, msg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    var langExpanded by remember { mutableStateOf(false) }
    val languages = remember { listOf("auto") + me.rerere.tts.qwen3.Qwen3TtsEngine.LANGUAGE_IDS.keys }
    FormItem(
        label = { Text(stringResource(R.string.local_tts_language)) },
        description = { Text(stringResource(R.string.local_tts_language_desc)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = langExpanded,
            onExpandedChange = { langExpanded = !langExpanded },
        ) {
            OutlinedTextField(
                value = setting.language,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
            )
            ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.replaceFirstChar { it.uppercase() }) },
                        onClick = {
                            langExpanded = false
                            onValueChange(setting.copy(language = lang))
                        },
                    )
                }
            }
        }
    }
}

/** Locally-injected OkHttp client for TTS bundle downloads. */
@Composable
private fun koinInjectOkHttp(): OkHttpClient = koinInject<OkHttpClient>()

private fun openModelSourceUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: Exception) {
        // no handler — safe no-op
    }
}

private suspend fun copyModelToAppDir(context: Context, uri: Uri, subDir: String): File? = withContext(Dispatchers.IO) {
    val dir = File(context.filesDir, "models/$subDir").apply { mkdirs() }
    val name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "${subDir}_${System.currentTimeMillis()}"
    val target = File(dir, name)
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext null
        target
    } catch (_: Exception) { null }
}
