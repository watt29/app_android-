package com.example.nongkanvelaassistant.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat

/** Owns the Android Telecom integration for normal calls and SOS calls. */
class TelecomCallController(private val context: Context) {

    fun placeNormalCall(number: String): Boolean = placeCall(number, requestSpeakerphone = false)

    fun placeEmergencyCall(number: String): Boolean {
        val placed = placeCall(number, requestSpeakerphone = true)
        if (placed) requestEmergencySpeakerphone()
        return placed
    }

    fun createDialFallbackIntent(number: String): Intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
            ?.takeIf { it.isNotBlank() }
            ?.let(::setPackage)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun placeCall(number: String, requestSpeakerphone: Boolean): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return runCatching {
            val extras = Bundle().apply {
                if (requestSpeakerphone) {
                    putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
                }
            }
            context.getSystemService(TelecomManager::class.java)
                ?.placeCall(Uri.parse("tel:$number"), extras)
                ?: error("Telecom service unavailable")
        }.onFailure { Log.e(TAG, "Unable to place call", it) }.isSuccess
    }

    @Suppress("DEPRECATION")
    private fun requestEmergencySpeakerphone() {
        val handler = Handler(Looper.getMainLooper())
        listOf(1_000L, 2_500L, 5_000L, 8_000L, 12_000L).forEach { delayMillis ->
            handler.postDelayed({
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                    audioManager.isSpeakerphoneOn = true
                    Log.d(TAG, "Requested speakerphone for emergency call")
                }
            }, delayMillis)
        }
    }

    private companion object {
        const val TAG = "TelecomCallController"
    }
}
