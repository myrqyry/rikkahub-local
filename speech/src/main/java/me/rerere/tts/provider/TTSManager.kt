package me.rerere.tts.provider

import android.content.Context
import kotlinx.coroutines.flow.Flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.providers.ElevenLabsTTSProvider
import me.rerere.tts.provider.providers.FishAudioTTSProvider
import me.rerere.tts.provider.providers.GeminiTTSProvider
import me.rerere.tts.provider.providers.GroqTTSProvider
import me.rerere.tts.provider.providers.MiMoTTSProvider
import me.rerere.tts.provider.providers.MiniMaxTTSProvider
import me.rerere.tts.provider.providers.NekoSpeakTTSProvider
import me.rerere.tts.provider.providers.OpenAITTSProvider
import me.rerere.tts.provider.providers.KittenTTSProvider
import me.rerere.tts.provider.providers.MatchaTTSProvider
import me.rerere.tts.provider.providers.PocketTTSProvider
import me.rerere.tts.provider.providers.QwenTTSProvider
import me.rerere.tts.provider.providers.Qwen3TtsProvider
import me.rerere.tts.provider.providers.StepTTSProvider
import me.rerere.tts.provider.providers.SystemTTSProvider
import me.rerere.tts.provider.providers.XAITTSProvider

class TTSManager(private val context: Context) {
    private val openAIProvider = OpenAITTSProvider()
    private val geminiProvider = GeminiTTSProvider()
    private val systemProvider = SystemTTSProvider()
    private val miniMaxProvider = MiniMaxTTSProvider()
    private val qwenProvider = QwenTTSProvider()
    private val groqProvider = GroqTTSProvider()
    private val xaiProvider = XAITTSProvider()
    private val miMoProvider = MiMoTTSProvider()
    private val stepProvider = StepTTSProvider()
    private val elevenLabsProvider = ElevenLabsTTSProvider()
    private val fishAudioProvider = FishAudioTTSProvider()
    private val nekoSpeakProvider = NekoSpeakTTSProvider()
    private val pocketTTSProvider = PocketTTSProvider()
    private val kittenTTSProvider = KittenTTSProvider()
    private val qwen3TTSProvider = Qwen3TtsProvider()
    private val matchaTTSProvider = MatchaTTSProvider()

    fun generateSpeech(
        providerSetting: TTSProviderSetting,
        request: TTSRequest
    ): Flow<AudioChunk> {
        return when (providerSetting) {
            is TTSProviderSetting.OpenAI -> openAIProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.Gemini -> geminiProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.SystemTTS -> systemProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.MiniMax -> miniMaxProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.Qwen -> qwenProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.Groq -> groqProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.XAI -> xaiProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.MiMo -> miMoProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.ElevenLabs -> elevenLabsProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.FishAudio -> fishAudioProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.Step -> stepProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.NekoSpeakTts -> nekoSpeakProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.PocketTts -> pocketTTSProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.KittenTts -> kittenTTSProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.Qwen3Tts -> qwen3TTSProvider.generateSpeech(context, providerSetting, request)
            is TTSProviderSetting.MatchaTts -> matchaTTSProvider.generateSpeech(context, providerSetting, request)
        }
    }

    /**
     * 返回该 provider 硬编码的语气标记引导提示词（默认空）。
     * 供 text_to_speech 工具注入 system prompt 使用。
     */
    fun getPromptGuidance(providerSetting: TTSProviderSetting): String {
        return when (providerSetting) {
            is TTSProviderSetting.OpenAI -> openAIProvider.promptGuidance
            is TTSProviderSetting.Gemini -> geminiProvider.promptGuidance
            is TTSProviderSetting.SystemTTS -> systemProvider.promptGuidance
            is TTSProviderSetting.MiniMax -> miniMaxProvider.promptGuidance
            is TTSProviderSetting.Qwen -> qwenProvider.promptGuidance
            is TTSProviderSetting.Groq -> groqProvider.promptGuidance
            is TTSProviderSetting.XAI -> xaiProvider.promptGuidance
            is TTSProviderSetting.MiMo -> miMoProvider.promptGuidance
            is TTSProviderSetting.ElevenLabs -> elevenLabsProvider.promptGuidance
            is TTSProviderSetting.FishAudio -> fishAudioProvider.promptGuidance
            is TTSProviderSetting.Step -> stepProvider.promptGuidance
            is TTSProviderSetting.NekoSpeakTts -> nekoSpeakProvider.promptGuidance
            is TTSProviderSetting.PocketTts -> pocketTTSProvider.promptGuidance
            is TTSProviderSetting.KittenTts -> kittenTTSProvider.promptGuidance
            is TTSProviderSetting.Qwen3Tts -> qwen3TTSProvider.promptGuidance
            is TTSProviderSetting.MatchaTts -> matchaTTSProvider.promptGuidance
        }
    }

    private var currentSetting: TTSProviderSetting? = null

    fun onSessionStart(providerSetting: TTSProviderSetting) {
        currentSetting = providerSetting
        when (providerSetting) {
            is TTSProviderSetting.OpenAI -> openAIProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.Gemini -> geminiProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.SystemTTS -> systemProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.MiniMax -> miniMaxProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.Qwen -> qwenProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.Groq -> groqProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.XAI -> xaiProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.MiMo -> miMoProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.ElevenLabs -> elevenLabsProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.FishAudio -> fishAudioProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.Step -> stepProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.NekoSpeakTts -> nekoSpeakProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.PocketTts -> pocketTTSProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.KittenTts -> kittenTTSProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.Qwen3Tts -> qwen3TTSProvider.onSessionStart(providerSetting)
            is TTSProviderSetting.MatchaTts -> matchaTTSProvider.onSessionStart(providerSetting)
        }
    }

    fun onSessionEnd() {
        when (currentSetting) {
            is TTSProviderSetting.OpenAI -> openAIProvider.onSessionEnd()
            is TTSProviderSetting.Gemini -> geminiProvider.onSessionEnd()
            is TTSProviderSetting.SystemTTS -> systemProvider.onSessionEnd()
            is TTSProviderSetting.MiniMax -> miniMaxProvider.onSessionEnd()
            is TTSProviderSetting.Qwen -> qwenProvider.onSessionEnd()
            is TTSProviderSetting.Groq -> groqProvider.onSessionEnd()
            is TTSProviderSetting.XAI -> xaiProvider.onSessionEnd()
            is TTSProviderSetting.MiMo -> miMoProvider.onSessionEnd()
            is TTSProviderSetting.ElevenLabs -> elevenLabsProvider.onSessionEnd()
            is TTSProviderSetting.FishAudio -> fishAudioProvider.onSessionEnd()
            is TTSProviderSetting.Step -> stepProvider.onSessionEnd()
            is TTSProviderSetting.NekoSpeakTts -> nekoSpeakProvider.onSessionEnd()
            is TTSProviderSetting.PocketTts -> pocketTTSProvider.onSessionEnd()
            is TTSProviderSetting.KittenTts -> kittenTTSProvider.onSessionEnd()
            is TTSProviderSetting.Qwen3Tts -> qwen3TTSProvider.onSessionEnd()
            is TTSProviderSetting.MatchaTts -> matchaTTSProvider.onSessionEnd()
            null -> Unit
        }
        currentSetting = null
    }

    fun reusesEngine(providerSetting: TTSProviderSetting): Boolean {
        return when (providerSetting) {
            is TTSProviderSetting.Qwen3Tts -> qwen3TTSProvider.reusesEngine
            is TTSProviderSetting.PocketTts -> pocketTTSProvider.reusesEngine
            is TTSProviderSetting.KittenTts -> kittenTTSProvider.reusesEngine
            is TTSProviderSetting.MatchaTts -> matchaTTSProvider.reusesEngine
            else -> false
        }
    }
}
