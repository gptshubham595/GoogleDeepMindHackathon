package com.androidblunders.rakshak.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Global, observable status of the live-call audio socket.
 *
 * [AudioStreamingManager] and [CallAudioStreamingService] write here; the UI
 * observes it to show whether the socket is connected and how many audio bytes
 * are flowing in each direction — so "is anything actually happening on a call"
 * is visible at a glance.
 */
object CallStreamStatus {

    enum class Connection { IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR }

    private val _active = MutableStateFlow(false)          // recording service running
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _connection = MutableStateFlow(Connection.IDLE)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private val _bytesSent = MutableStateFlow(0L)          // PCM bytes → VOICE
    val bytesSent: StateFlow<Long> = _bytesSent.asStateFlow()

    private val _bytesReceived = MutableStateFlow(0L)      // JSON/text bytes ← VOICE
    val bytesReceived: StateFlow<Long> = _bytesReceived.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _serverThreat = MutableStateFlow(0f)
    val serverThreat: StateFlow<Float> = _serverThreat.asStateFlow()

    private val _serverState = MutableStateFlow("MONITORING")
    val serverState: StateFlow<String> = _serverState.asStateFlow()

    fun setActive(active: Boolean) { _active.value = active }
    fun setConnection(c: Connection) { _connection.value = c }
    fun addSent(bytes: Int) { _bytesSent.update { it + bytes } }
    fun addReceived(bytes: Int) { _bytesReceived.update { it + bytes } }
    fun setTranscript(text: String) { _transcript.value = text }
    fun setServerThreat(score: Float) { _serverThreat.value = score }
    fun setServerState(state: String) { _serverState.value = state }

    fun reset() {
        _connection.value = Connection.IDLE
        _bytesSent.value = 0L
        _bytesReceived.value = 0L
        _transcript.value = ""
        _serverThreat.value = 0f
        _serverState.value = "MONITORING"
    }
}
