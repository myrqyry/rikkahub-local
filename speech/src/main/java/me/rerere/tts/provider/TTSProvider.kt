package me.rerere.tts.provider

import android.content.Context
import kotlinx.coroutines.flow.Flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest

interface TTSProvider<T : TTSProviderSetting> {
    fun generateSpeech(
        context: Context,
        providerSetting: T,
        request: TTSRequest
    ): Flow<AudioChunk>

    /**
     * 可选：指导 AI 如何在朗读文本中加入该 provider 支持的语气/情感标记的提示词。
     *
     * 默认空 = 不注入。支持内联标记的 provider 直接在实现类里覆盖此值（硬编码）。
     * 该内容会在 text_to_speech 工具启用时被追加进 system prompt。
     *
     * 注意（写内容时的两个约束）：
     * 1. 要求 AI 仅把标记放进 text_to_speech 工具的 text 参数，不要出现在给用户看的正文里；
     * 2. 标记需能扛过清洗管线：避免 `*` `_` 等会被 stripMarkdown 删除的符号，
     *    且标记内部不要含 `。，！？…` 等会被 TextChunker 切断的标点。优先用方括号/尖括号。
     */
    val promptGuidance: String
        get() = ""

    /**
     * True when the provider keeps a reusable engine across chunks (local models).
     * The controller uses this to disable speculative prefetch and evict completed
     * PCM from the cache.
     */
    val reusesEngine: Boolean
        get() = false

    /**
     * Session start hook, called by TtsController when a speak() session starts.
     * Engine acquisition is lazy (first generateSpeech), so this only records
     * intent and is safe to call on the main thread.
     */
    fun onSessionStart(setting: T) = Unit

    /** Session end hook, called on stop()/dispose()/provider change. Releases the cached engine. */
    fun onSessionEnd() = Unit
}
