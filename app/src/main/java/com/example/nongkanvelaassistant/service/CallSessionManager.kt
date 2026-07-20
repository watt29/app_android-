package com.example.nongkanvelaassistant.service

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallUiState(
    val isVisible: Boolean = false,
    val isIncoming: Boolean = false,
    val isActive: Boolean = false,
    val displayName: String = "สายโทรเข้า",
    val isUnknownCaller: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false
)

object CallSessionManager {
    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()
    private var service: InCallService? = null
    private var call: Call? = null
    private var callerIdentity: CallerIdentity? = null

    fun attachService(value: InCallService) { service = value }
    fun detachService(value: InCallService) { if (service === value) service = null }

    fun attachCall(value: Call) {
        call = value
        value.registerCallback(callback)
        update(value)
    }

    fun setCallerIdentity(value: Call, displayName: String, isKnownContact: Boolean) {
        if (call === value) {
            callerIdentity = CallerIdentity(displayName, isKnownContact)
            update(value)
        }
    }

    fun removeCall(value: Call) {
        value.unregisterCallback(callback)
        if (call === value) {
            call = null
            callerIdentity = null
            _uiState.value = CallUiState()
        }
    }

    fun answer() { call?.answer(VideoProfile.STATE_AUDIO_ONLY) }
    fun disconnect() { call?.disconnect() }
    fun toggleMute() { service?.setMuted(!_uiState.value.isMuted) }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        service?.setAudioRoute(if (_uiState.value.isSpeakerOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }

    @Suppress("DEPRECATION")
    fun onAudioStateChanged(state: CallAudioState?) {
        val speaker = state?.route?.and(CallAudioState.ROUTE_SPEAKER) != 0
        val muted = state?.isMuted ?: false
        _uiState.value = _uiState.value.copy(isSpeakerOn = speaker, isMuted = muted)
    }

    private fun update(value: Call) {
        val details = value.details
        val identity = callerIdentity
        val name = identity?.displayName
            ?: details?.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
            ?: "สายโทรศัพท์"
        _uiState.value = _uiState.value.copy(
            isVisible = value.state != Call.STATE_DISCONNECTED,
            isIncoming = value.state == Call.STATE_RINGING,
            isActive = value.state == Call.STATE_ACTIVE,
            displayName = name,
            isUnknownCaller = value.state == Call.STATE_RINGING && identity?.isKnownContact == false
        )
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = update(call)
        override fun onDetailsChanged(call: Call, details: Call.Details) = update(call)
    }

    private data class CallerIdentity(val displayName: String, val isKnownContact: Boolean)
}
