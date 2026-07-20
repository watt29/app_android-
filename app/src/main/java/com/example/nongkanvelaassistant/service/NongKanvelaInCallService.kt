package com.example.nongkanvelaassistant.service

import android.content.pm.PackageManager
import android.content.Intent
import android.Manifest
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.example.nongkanvelaassistant.ui.CallActivity
import java.util.Locale

class NongKanvelaInCallService : InCallService(), TextToSpeech.OnInitListener {
    private val callCallbacks = mutableMapOf<Call, Call.Callback>()
    private val announcedCalls = mutableSetOf<Call>()
    private var textToSpeech: TextToSpeech? = null
    private var isTextToSpeechReady = false
    private var pendingCallerName: String? = null
    private var pendingUnknownCallerWarning = false

    override fun onCreate() {
        super.onCreate()
        CallSessionManager.attachService(this)
        textToSpeech = TextToSpeech(this, this)
    }

    override fun onDestroy() {
        callCallbacks.forEach { (call, callback) -> call.unregisterCallback(callback) }
        callCallbacks.clear()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        CallSessionManager.detachService(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallSessionManager.attachCall(call)
        val caller = findCaller(call)
        CallSessionManager.setCallerIdentity(call, caller.label, caller.isKnownContact)
        val callback = object : Call.Callback() {
            override fun onStateChanged(changedCall: Call, state: Int) {
                if (state == Call.STATE_RINGING) announceIncomingCall(changedCall, caller)
            }
        }
        callCallbacks[call] = callback
        call.registerCallback(callback)
        if (call.state == Call.STATE_RINGING) announceIncomingCall(call, caller)
        startActivity(Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    override fun onCallRemoved(call: Call) {
        callCallbacks.remove(call)?.let(call::unregisterCallback)
        announcedCalls.remove(call)
        CallSessionManager.removeCall(call)
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallSessionManager.onAudioStateChanged(audioState)
    }

    override fun onInit(status: Int) {
        isTextToSpeechReady = status == TextToSpeech.SUCCESS
        if (isTextToSpeechReady) {
            textToSpeech?.language = Locale("th", "TH")
            pendingCallerName?.let { caller ->
                if (pendingUnknownCallerWarning) speakUnknownCallerWarning(caller) else speakIncomingCaller(caller)
            }
            pendingCallerName = null
            pendingUnknownCallerWarning = false
        }
    }

    private fun announceIncomingCall(call: Call, caller: Caller) {
        if (!announcedCalls.add(call)) return
        if (!caller.isKnownContact) {
            speakUnknownCallerWarning(caller.label)
            return
        }
        val callerName = caller.label
        if (isTextToSpeechReady) {
            speakIncomingCaller(callerName)
        } else {
            pendingCallerName = callerName
        }
    }

    private fun speakUnknownCallerWarning(number: String) {
        val message = "สายจากหมายเลข $number ไม่อยู่ในรายชื่อค่ะ หากไม่แน่ใจ ไม่ต้องรับสาย"
        if (isTextToSpeechReady) {
            speakThai(message, TextToSpeech.QUEUE_FLUSH)
        } else {
            pendingCallerName = number
            pendingUnknownCallerWarning = true
        }
    }

    private fun speakIncomingCaller(callerName: String) {
        if (callerName.any { it in 'A'..'Z' || it in 'a'..'z' }) {
            speakThai("สายจาก", TextToSpeech.QUEUE_FLUSH)
            speakEnglish(callerName)
            speakThai("กำลังโทรมาค่ะ", TextToSpeech.QUEUE_ADD)
        } else {
            speakThai("สายจาก $callerName กำลังโทรมาค่ะ", TextToSpeech.QUEUE_FLUSH)
        }
    }

    private fun speakThai(message: String, queueMode: Int) {
        textToSpeech?.language = Locale("th", "TH")
        textToSpeech?.speak(message, queueMode, null, "incoming-caller-thai")
    }

    private fun speakEnglish(message: String) {
        val languageResult = textToSpeech?.setLanguage(Locale.US) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (languageResult >= TextToSpeech.LANG_AVAILABLE) {
            textToSpeech?.speak(message, TextToSpeech.QUEUE_ADD, null, "incoming-caller-english")
        } else {
            speakThai(message, TextToSpeech.QUEUE_ADD)
        }
    }

    private fun findCaller(call: Call): Caller {
        val details = call.details
        val number = details.handle?.schemeSpecificPart.orEmpty()
        if (number.isBlank() || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return Caller(number.ifBlank { "หมายเลขที่ไม่ทราบชื่อ" }, false)
        }
        val lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        contentResolver.query(
            lookupUri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return Caller(it, true) }
            }
        }
        return Caller(number, false)
    }

    private data class Caller(val label: String, val isKnownContact: Boolean)
}
