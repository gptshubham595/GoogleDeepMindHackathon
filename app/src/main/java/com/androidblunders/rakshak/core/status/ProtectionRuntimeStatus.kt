package com.androidblunders.rakshak.core.status

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-wide, read-only UI telemetry for the real protection pipeline. */
object ProtectionRuntimeStatus {
    enum class SttState { IDLE, CONNECTING, LISTENING, RECEIVING, ERROR }
    enum class TtsState { INITIALIZING, READY, SPEAKING, ERROR }

    private val _sttState = MutableStateFlow(SttState.IDLE)
    val sttState: StateFlow<SttState> = _sttState.asStateFlow()

    private val _ttsState = MutableStateFlow(TtsState.INITIALIZING)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private val _lastInput = MutableStateFlow("Waiting for call or message")
    val lastInput: StateFlow<String> = _lastInput.asStateFlow()

    private val _lastInputAt = MutableStateFlow(0L)
    val lastInputAt: StateFlow<Long> = _lastInputAt.asStateFlow()

    private val _messageCount = MutableStateFlow(0L)
    val messageCount: StateFlow<Long> = _messageCount.asStateFlow()

    private val _lastSpokenWarning = MutableStateFlow("")
    val lastSpokenWarning: StateFlow<String> = _lastSpokenWarning.asStateFlow()

    fun setStt(state: SttState) {
        _sttState.value = state
    }

    fun setTts(state: TtsState) {
        _ttsState.value = state
    }

    fun markMessage(sender: String, source: String) {
        _messageCount.update { it + 1 }
        _lastInput.value = "Message from $sender · $source"
        _lastInputAt.value = System.currentTimeMillis()
    }

    fun markCallActivated() {
        _lastInput.value = "Connected call protection activated"
        _lastInputAt.value = System.currentTimeMillis()
    }

    fun markTranscript(text: String) {
        _sttState.value = SttState.RECEIVING
        _lastInput.value = "Live speech received · ${text.take(60)}"
        _lastInputAt.value = System.currentTimeMillis()
    }

    fun markSpokenWarning(text: String) {
        _lastSpokenWarning.value = text
    }
}
