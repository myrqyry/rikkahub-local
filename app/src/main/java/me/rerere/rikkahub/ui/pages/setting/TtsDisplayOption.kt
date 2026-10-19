package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.data.datastore.DisplaySetting

/** Targeted updates ensure toggling one TTS option preserves other settings. */
enum class TtsDisplayOption {
    QUOTED_ONLY, SKIP_BRACKETS, AUTO_PLAY,
}

fun DisplaySetting.withTtsOption(option: TtsDisplayOption, enabled: Boolean): DisplaySetting =
    when (option) {
        TtsDisplayOption.QUOTED_ONLY -> copy(ttsOnlyReadQuoted = enabled)
        TtsDisplayOption.SKIP_BRACKETS -> copy(ttsOnlyReadOutsideBrackets = enabled)
        TtsDisplayOption.AUTO_PLAY -> copy(autoPlayTTSAfterGeneration = enabled)
    }
