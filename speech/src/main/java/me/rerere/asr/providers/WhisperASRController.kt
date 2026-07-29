package me.rerere.asr.providers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus

class WhisperASRController(
    private val appContext: Context,
    private val provider: ASRProviderSetting.WhisperAsr,
) : ASRController {
    private val _state = MutableStateFlow(ASRState(status = ASRStatus.Idle, isAvailable = true))
    override val state: StateFlow<ASRState> = _state

    private var nativePtr: Long = 0
    private var isRunning = false

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (isRunning) return
        isRunning = true
        _state.value = _state.value.copy(status = ASRStatus.Listening)

        nativePtr = nativeInit(
            modelPath = provider.modelPath,
            language = provider.language,
            sampleRate = provider.sampleRate,
        )
        nativeStart(nativePtr, object : WhisperCallback {
            override fun onTranscript(text: String) {
                _state.value = _state.value.copy(transcript = text)
                onTranscriptChange(text)
            }

            override fun onError(message: String) {
                _state.value = _state.value.copy(
                    status = ASRStatus.Error,
                    errorMessage = message,
                )
            }
        })
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false
        _state.value = _state.value.copy(status = ASRStatus.Stopping)
        if (nativePtr != 0L) {
            nativeStop(nativePtr)
            nativeRelease(nativePtr)
            nativePtr = 0
        }
        _state.value = _state.value.copy(status = ASRStatus.Idle)
    }

    override fun dispose() {
        stop()
    }

    private external fun nativeInit(modelPath: String, language: String, sampleRate: Int): Long
    private external fun nativeStart(ptr: Long, callback: WhisperCallback)
    private external fun nativeStop(ptr: Long)
    private external fun nativeRelease(ptr: Long)

    companion object {
        private var loaded = false
        fun ensureLoaded() {
            if (!loaded) {
                System.loadLibrary("whisper")
                loaded = true
            }
        }
    }
}

interface WhisperCallback {
    fun onTranscript(text: String)
    fun onError(message: String)
}