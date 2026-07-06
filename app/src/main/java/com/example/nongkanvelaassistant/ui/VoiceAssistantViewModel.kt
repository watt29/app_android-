package com.example.nongkanvelaassistant.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nongkanvelaassistant.data.NongKanvelaRepository
import com.example.nongkanvelaassistant.data.GroqKeyResolver
import com.example.nongkanvelaassistant.BuildConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import android.os.BatteryManager
import android.content.IntentFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import com.example.nongkanvelaassistant.data.LocalAssistantBot
import com.example.nongkanvelaassistant.data.ContactFallbackResolver

data class ChatLog(
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String,
    val message: String,
    val location: String = "ไม่ทราบตำแหน่ง"
)

class VoiceAssistantViewModel(application: Application) : AndroidViewModel(application), RecognitionListener, TextToSpeech.OnInitListener {

    private val repository = NongKanvelaRepository()
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private val context: Context get() = getApplication<Application>().applicationContext
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    private val _uiState = MutableStateFlow(VoiceAssistantState())
    val uiState: StateFlow<VoiceAssistantState> = _uiState.asStateFlow()

    private val _conversationHistory = MutableStateFlow<List<ChatLog>>(emptyList())
    val conversationHistory: StateFlow<List<ChatLog>> = _conversationHistory.asStateFlow()

    private val prefs = context.getSharedPreferences("nongkanvela_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val httpClient = OkHttpClient()
    private val secureSettingsStore = com.example.nongkanvelaassistant.data.SecureSettingsStore(context)

    private val defaultToken = "tbh0AIs4HTO/ckFqrWbsnR4CakkdWbNyrlkMbVm1QF+fRqVuCG/AJ6HGr7EIOnyFB9VXpmb88HtHVmr8BAv5qKmDYgNnaVi322Jj9Bc2g6rE9ZhWKnEN5ROI9UnjuYnggGY9mNaC+yHnTiGX+u7IYwdB04t89/1O/w1cDnyilFU="
    private val defaultUserId = "Uc88eb3896b0e4bcc5fbaa9b78ac1294e"

    private val _lineToken = MutableStateFlow(prefs.getString("line_token", defaultToken) ?: defaultToken)
    val lineToken: StateFlow<String> = _lineToken.asStateFlow()

    private val _lineUserId = MutableStateFlow(prefs.getString("line_user_id", defaultUserId) ?: defaultUserId)
    val lineUserId: StateFlow<String> = _lineUserId.asStateFlow()

    private val _elderName = MutableStateFlow(prefs.getString("elder_name", "คุณตา/คุณยาย") ?: "คุณตา/คุณยาย")
    val elderName: StateFlow<String> = _elderName.asStateFlow()

    private val _deviceRole = MutableStateFlow(prefs.getString("device_role", "elder") ?: "elder")
    val deviceRole: StateFlow<String> = _deviceRole.asStateFlow()

    private val defaultGoogleSheetUrl = "https://script.google.com/macros/s/AKfycbxdtIGggPhiEJ0QkGkR-5zre9gEQyRqme7-wqMbDJtqebOIrXk68SSLEtyrPn7fL3QW/exec"

    private val _googleSheetUrl = MutableStateFlow(prefs.getString("google_sheet_url", defaultGoogleSheetUrl) ?: defaultGoogleSheetUrl)
    val googleSheetUrl: StateFlow<String> = _googleSheetUrl.asStateFlow()

    private val _groqKeysString = MutableStateFlow("")
    val groqKeysString: StateFlow<String> = _groqKeysString.asStateFlow()

    private val _remindersList = MutableStateFlow<List<ReminderItem>>(emptyList())
    val remindersList: StateFlow<List<ReminderItem>> = _remindersList.asStateFlow()

    init {
        loadHistoryFromDisk()
        textToSpeech = TextToSpeech(context, this)
        setupSpeechRecognizer()
        loadReminders()
        secureSettingsStore.migrateGroqKeysFrom(prefs)
        val storedGroqKeys = secureSettingsStore.getGroqKeysCsv()
        _groqKeysString.value = if (storedGroqKeys.isNotBlank()) {
            storedGroqKeys
        } else {
            GroqKeyResolver.resolve(BuildConfig.GROQ_API_KEY).joinToString(",")
        }
    }

    fun loadReminders() {
        viewModelScope.launch(Dispatchers.IO) {
            _remindersList.value = com.example.nongkanvelaassistant.data.ReminderStorage.load(context)
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.nongkanvelaassistant.data.ReminderScheduler.cancel(context, reminderId)
            com.example.nongkanvelaassistant.data.ReminderStorage.remove(context, reminderId)
            loadReminders()
        }
    }

    private fun loadHistoryFromDisk() {
        try {
            val json = prefs.getString("chat_history", null)
            if (json != null) {
                val type = object : TypeToken<List<ChatLog>>() {}.type
                val history: List<ChatLog> = gson.fromJson(json, type)
                _conversationHistory.value = history
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveHistoryToDisk(history: List<ChatLog>) {
        try {
            val json = gson.toJson(history)
            prefs.edit().putString("chat_history", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setElderName(name: String) {
        prefs.edit().putString("elder_name", name).apply()
        _elderName.value = name
    }

    fun setDeviceRole(role: String) {
        prefs.edit().putString("device_role", role).apply()
        _deviceRole.value = role
    }

    fun setLineConfig(token: String, userId: String) {
        prefs.edit()
            .putString("line_token", token)
            .putString("line_user_id", userId)
            .apply()
        _lineToken.value = token
        _lineUserId.value = userId
    }

    fun setGoogleSheetUrl(url: String) {
        prefs.edit().putString("google_sheet_url", url).apply()
        _googleSheetUrl.value = url
    }

    fun setGroqKeys(keysCsv: String) {
        val normalizedKeys = keysCsv.trim()
        secureSettingsStore.setGroqKeysCsv(normalizedKeys)
        _groqKeysString.value = normalizedKeys
    }

    fun getGroqApiKeys(): List<String> {
        return GroqKeyResolver.resolve(_groqKeysString.value)
    }

    private fun sendLinePushMessage(userMessage: String, aiMessage: String, location: String) {
        val token = _lineToken.value
        val targetUser = _lineUserId.value
        if (token.isBlank() || targetUser.isBlank()) return

        val textContent = "ออเดอร์ใหม่จากแอป (เครื่อง: ${_elderName.value})\n-----------------\n[ผู้ใช้]: $userMessage\n[อุ่นใจ]: $aiMessage\n[พิกัด]: $location"
        
        // Construct LINE API JSON Payload
        val payloadMap = mapOf(
            "to" to targetUser,
            "messages" to listOf(
                mapOf(
                    "type" to "text",
                    "text" to textContent
                )
            )
        )
        val jsonPayload = gson.toJson(payloadMap)

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = jsonPayload.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://api.line.me/v2/bot/message/push")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    fun sendAdminNotification(message: String, isSync: Boolean = false) {
        val token = _lineToken.value
        val targetUser = _lineUserId.value
        if (token.isBlank() || targetUser.isBlank()) return

        val formattedMessage = "[เครื่อง: ${_elderName.value}] $message"

        val payloadMap = mapOf(
            "to" to targetUser,
            "messages" to listOf(
                mapOf(
                    "type" to "text",
                    "text" to formattedMessage
                )
            )
        )
        val jsonPayload = gson.toJson(payloadMap)
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = jsonPayload.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://api.line.me/v2/bot/message/push")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        if (isSync) {
            val thread = Thread {
                try {
                    httpClient.newCall(request).execute().use { response ->
                        response.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            thread.start()
            try {
                thread.join(2000) // Wait up to 2 seconds for shutdown delivery
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        } else {
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { e.printStackTrace() }
                override fun onResponse(call: Call, response: Response) { response.close() }
            })
        }
    }

    private fun sendGoogleSheetMessage(userMessage: String, aiMessage: String, location: String) {
        val url = _googleSheetUrl.value
        if (url.isBlank()) return

        val payloadMap = mapOf(
            "timestamp" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            "customerMessage" to "[${_elderName.value}] $userMessage",
            "aiMessage" to aiMessage,
            "location" to location
        )
        val jsonPayload = gson.toJson(payloadMap)

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = jsonPayload.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    fun syncHistoryToGoogleSheet(onResult: (String) -> Unit) {
        val url = _googleSheetUrl.value
        if (url.isBlank()) {
            onResult("กรุณาตั้งค่า Google Sheets URL ก่อนค่ะ")
            return
        }
        val history = _conversationHistory.value
        if (history.isEmpty()) {
            onResult("ไม่มีประวัติบทสนทนาที่จะบันทึกค่ะ")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0
            val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

            for (log in history) {
                val payloadMap = mapOf(
                    "timestamp" to df.format(java.util.Date(log.timestamp)),
                    "customerMessage" to if (log.sender != "อุ่นใจ") "[${_elderName.value}] ${log.message}" else "",
                    "aiMessage" to if (log.sender == "อุ่นใจ") log.message else "",
                    "location" to log.location
                )
                val jsonPayload = gson.toJson(payloadMap)
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = jsonPayload.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            successCount++
                        } else {
                            failCount++
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    failCount++
                }
            }

            viewModelScope.launch(Dispatchers.Main) {
                if (failCount == 0) {
                    onResult("บันทึกสำเร็จทั้งหมด $successCount รายการลง Google Sheets แล้วค่ะ")
                } else {
                    onResult("บันทึกสำเร็จ $successCount รายการ, ล้มเหลว $failCount รายการ")
                }
            }
        }
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
        } else {
            _uiState.value = _uiState.value.copy(errorMessage = "Speech recognition is not available on this device.")
        }
    }

    fun startListening() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@VoiceAssistantViewModel)
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH") // Thai language
            putExtra(RecognizerIntent.EXTRA_PROMPT, "พูดมาได้เลยค่ะ น้องกาลเวลารอฟังอยู่")
        }
        _uiState.value = _uiState.value.copy(isListening = true, transcribedText = "กำลังฟัง...", aiResponse = "")
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _uiState.value = _uiState.value.copy(isListening = false)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("th", "TH"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                _uiState.value = _uiState.value.copy(errorMessage = "Thai language is not supported for Text-to-Speech on this device.")
            } else {
                textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        viewModelScope.launch(Dispatchers.Main) {
                            stopListening()
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "AiResponse") {
                            viewModelScope.launch(Dispatchers.Main) {
                                startListening()
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        super.onError(utteranceId, errorCode)
                    }
                })
            }
        }
    }

    fun processUserText(text: String) {
        processUserSpeech(text, isFromAdmin = false)
    }

    fun processAdminCommand(text: String) {
        processUserSpeech(text, isFromAdmin = true)
    }

    private fun getInstalledApps(): List<Pair<String, String>> {
        val pm = context.packageManager
        val appsList = mutableListOf<Pair<String, String>>()
        try {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            for (resolveInfo in resolveInfos) {
                val appName = resolveInfo.loadLabel(pm).toString()
                val packageName = resolveInfo.activityInfo.packageName
                if (appName.isNotBlank() && packageName.isNotBlank()) {
                    appsList.add(Pair(appName, packageName))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return appsList
    }

    private fun setFlashlightState(enabled: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enabled)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getDeviceContactsAsDeviceContactList(): List<DeviceContact> {
        val contactsList = mutableListOf<DeviceContact>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return contactsList
        }
        
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                android.provider.ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )
        
        cursor?.use {
            val idIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val rawIdIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
            val nameIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
            
            while (it.moveToNext()) {
                if (nameIndex >= 0 && numberIndex >= 0) {
                    val contactId = if (idIndex >= 0) it.getLong(idIndex) else 0L
                    val rawContactId = if (rawIdIndex >= 0) it.getLong(rawIdIndex) else 0L
                    val name = it.getString(nameIndex) ?: ""
                    val number = it.getString(numberIndex) ?: ""
                    if (name.isNotBlank() && number.isNotBlank()) {
                        contactsList.add(DeviceContact(contactId, rawContactId, name, number))
                    }
                }
            }
        }
        return contactsList
    }

    private fun createContact(name: String, number: String): String {
        try {
            val ops = arrayListOf<android.content.ContentProviderOperation>()
            val rawContactInsertIndex = ops.size
            
            ops.add(android.content.ContentProviderOperation.newInsert(android.provider.ContactsContract.RawContacts.CONTENT_URI)
                .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build())
                
            ops.add(android.content.ContentProviderOperation.newInsert(android.provider.ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(android.provider.ContactsContract.Data.MIMETYPE, android.provider.ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(android.provider.ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build())
                
            ops.add(android.content.ContentProviderOperation.newInsert(android.provider.ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(android.provider.ContactsContract.Data.MIMETYPE, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                .withValue(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE, android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build())
                
            context.contentResolver.applyBatch(android.provider.ContactsContract.AUTHORITY, ops)
            return "บันทึกรายชื่อ $name เบอร์ $number เรียบร้อยแล้วค่ะ"
        } catch (e: Exception) {
            e.printStackTrace()
            return "ไม่สามารถบันทึกรายชื่อได้ค่ะ: ${e.message}"
        }
    }

    private fun deleteContact(contact: DeviceContact): String {
        try {
            val rows = context.contentResolver.delete(
                android.provider.ContactsContract.RawContacts.CONTENT_URI,
                "${android.provider.ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contact.contactId.toString())
            )
            return if (rows > 0) "ลบรายชื่อ ${contact.name} เรียบร้อยแล้วค่ะ" else "ไม่พบรายชื่อที่ต้องการลบในเครื่องค่ะ"
        } catch (e: Exception) {
            e.printStackTrace()
            return "ไม่สามารถลบรายชื่อได้ค่ะ: ${e.message}"
        }
    }

    private fun updateContact(contact: DeviceContact, newName: String, newNumber: String): String {
        try {
            val ops = arrayListOf<android.content.ContentProviderOperation>()
            
            ops.add(android.content.ContentProviderOperation.newUpdate(android.provider.ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${android.provider.ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${android.provider.ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(contact.rawContactId.toString(), android.provider.ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                )
                .withValue(android.provider.ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
                .build())
                
            ops.add(android.content.ContentProviderOperation.newUpdate(android.provider.ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${android.provider.ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${android.provider.ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(contact.rawContactId.toString(), android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                )
                .withValue(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, newNumber)
                .build())
                
            context.contentResolver.applyBatch(android.provider.ContactsContract.AUTHORITY, ops)
            return "แก้ไขรายชื่อเป็น $newName เบอร์ $newNumber เรียบร้อยแล้วค่ะ"
        } catch (e: Exception) {
            e.printStackTrace()
            return "ไม่สามารถแก้ไขรายชื่อได้ค่ะ: ${e.message}"
        }
    }

    private fun removeReminderByQuery(query: String): String? {
        val list = _remindersList.value
        val normalizedQuery = query.trim().replace(" ", "")
        if (normalizedQuery.isBlank()) return null
        
        val matched = list.firstOrNull { 
            it.title.trim().replace(" ", "").contains(normalizedQuery, ignoreCase = true) 
        }
        return if (matched != null) {
            deleteReminder(matched.id)
            "ลบรายการเตือน ${matched.title} เรียบร้อยแล้วค่ะ"
        } else {
            null
        }
    }

    private fun getReminderSummaryText(): String {
        val list = _remindersList.value
        if (list.isEmpty()) return "ยังไม่มีรายการเตือนค่ะ"
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return "รายการเตือนทั้งหมดของคุณมีดังนี้ค่ะ:\n" + list.joinToString("\n") { 
            val kindText = when (it.kind) {
                "medicine" -> "กินยา"
                "appointment" -> "นัดหมอ"
                "vaccine" -> "วัคซีน"
                else -> "ทั่วไป"
            }
            "- ${it.title} ($kindText) เวลา ${df.format(Date(it.triggerAtMillis))}"
        }
    }

    private fun getCurrentTimeText(): String {
        val df = SimpleDateFormat("HH:mm", Locale.getDefault())
        return "ขณะนี้เวลา ${df.format(Date())} ค่ะ"
    }

    private fun getTodayDateText(): String {
        val df = SimpleDateFormat("EEEEที่ d MMMM yyyy", Locale("th", "TH"))
        return "วันนี้คือวัน${df.format(Date())} ค่ะ"
    }

    private fun normalizeContactName(name: String): String {
        var temp = name.trim()
        val formalPrefixes = listOf("คุณ", "นาย", "นางสาว", "นาง")
        for (prefix in formalPrefixes) {
            if (temp.startsWith(prefix)) {
                temp = temp.substring(prefix.length).trim()
                break
            }
        }
        return temp.replace(Regex("[^\\p{L}\\p{N}\\u0e00-\\u0e7f\\s]"), "").trim().lowercase(java.util.Locale.getDefault())
    }

    private fun findMatchingContacts(query: String, contacts: List<Pair<String, String>>): List<Pair<String, String>> {
        val normalizedQuery = normalizeContactName(query)
        if (normalizedQuery.isBlank()) return emptyList()

        // 1. Direct Phone Number Detection (digits >= 6 chars)
        val digitOnly = query.replace(Regex("[^0-9]"), "")
        if (digitOnly.length >= 6) {
            return listOf(Pair(query, digitOnly))
        }

        // Normalize contacts and preserve original
        val normalizedContacts = contacts.map { Pair(it, normalizeContactName(it.first)) }

        // 2. Exact Match
        val exactMatches = normalizedContacts.filter { it.second == normalizedQuery }
        if (exactMatches.isNotEmpty()) {
            return exactMatches.map { it.first }
        }

        // 3. Substring / Word-boundary Match
        val queryWords = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
        val matches = mutableListOf<Pair<Pair<String, String>, Int>>() // Contact to Score

        for ((contact, normName) in normalizedContacts) {
            if (normName.isBlank()) continue
            var matched = false
            var score = 0

            if (queryWords.size == 1) {
                val q = queryWords[0]
                val contactWords = normName.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (contactWords.isNotEmpty()) {
                    val firstName = contactWords[0]
                    if (firstName == q) {
                        matched = true
                        score = 100
                    } else if (firstName.contains(q)) {
                        matched = true
                        score = 50
                    }
                    for (i in 1 until contactWords.size) {
                        if (contactWords[i] == q) {
                            matched = true
                            score = Math.max(score, 80)
                        }
                    }
                }
            } else {
                if (normName.contains(normalizedQuery)) {
                    matched = true
                    score = 90
                } else {
                    val contactWords = normName.split(Regex("\\s+")).filter { it.isNotBlank() }
                    var allWordsMatch = true
                    for (qw in queryWords) {
                        if (!contactWords.any { cw -> cw.contains(qw) }) {
                            allWordsMatch = false
                            break
                        }
                    }
                    if (allWordsMatch) {
                        matched = true
                        score = 40
                    }
                }
            }

            if (matched) {
                matches.add(Pair(contact, score))
            }
        }

        return matches.sortedByDescending { it.second }.map { it.first }
    }

    private fun tryLocalCommandFallback(text: String): String? {
        val bot = LocalAssistantBot()
        val contacts = getDeviceContactsAsDeviceContactList()
        val apps = getInstalledApps()
        
        val searchContacts: (String) -> List<DeviceContact> = { query ->
            ContactFallbackResolver.findTopMatchingContacts(query, contacts, limit = 10).map { it.contact }
        }
        
        return bot.handle(
            text = text,
            contacts = contacts,
            apps = apps,
            sheetCommands = emptyList(),
            scamNumbers = emptyList(),
            scamSources = emptyList(),
            contentPreferences = emptyList(),
            searchContacts = searchContacts,
            updateContact = { contact, newName, newNumber -> updateContact(contact, newName, newNumber) },
            deleteContact = { contact -> deleteContact(contact) },
            createContact = { name, number -> createContact(name, number) },
            readLatestMessage = { "ไม่มีข้อความใหม่ค่ะ" },
            getBatteryStatus = { 
                try {
                    val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val pct = (level * 100 / scale.toFloat()).toInt()
                    "ขณะนี้แบตเตอรี่ของเครื่องเหลือประมาณ $pct เปอร์เซ็นต์ค่ะ"
                } catch (e: Exception) {
                    "ไม่สามารถตรวจสอบแบตเตอรี่ได้ในขณะนี้ค่ะ"
                }
            },
            getCurrentTimeText = { getCurrentTimeText() },
            getTodayDateText = { getTodayDateText() },
            getReminderSummaryText = { getReminderSummaryText() },
            removeReminderByQuery = { query -> removeReminderByQuery(query) },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "อุ่นใจกำลังติดต่อสายฉุกเฉินให้ค่ะ" }
        )
    }

    private fun getDeviceContacts(): List<Pair<String, String>> {
        val contactsList = mutableListOf<Pair<String, String>>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return contactsList
        }
        
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )
        
        cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
            
            while (it.moveToNext()) {
                if (nameIndex >= 0 && numberIndex >= 0) {
                    val name = it.getString(nameIndex) ?: ""
                    val number = it.getString(numberIndex) ?: ""
                    if (name.isNotBlank() && number.isNotBlank()) {
                        contactsList.add(Pair(name, number))
                    }
                }
            }
        }
        return contactsList
    }

    private fun getDeviceCallLogs(): List<Map<String, String>> {
        val callLogsList = mutableListOf<Map<String, String>>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return callLogsList
        }

        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            android.provider.CallLog.Calls.CONTENT_URI,
            arrayOf(
                android.provider.CallLog.Calls.NUMBER,
                android.provider.CallLog.Calls.TYPE,
                android.provider.CallLog.Calls.DATE,
                android.provider.CallLog.Calls.DURATION,
                android.provider.CallLog.Calls.CACHED_NAME
            ),
            null,
            null,
            "${android.provider.CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            val numberIndex = it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
            val typeIndex = it.getColumnIndex(android.provider.CallLog.Calls.TYPE)
            val dateIndex = it.getColumnIndex(android.provider.CallLog.Calls.DATE)
            val durationIndex = it.getColumnIndex(android.provider.CallLog.Calls.DURATION)
            val nameIndex = it.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)

            var count = 0
            while (it.moveToNext() && count < 10) { // Get last 10 call logs
                val number = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
                val typeCode = if (typeIndex >= 0) it.getInt(typeIndex) else -1
                val dateMillis = if (dateIndex >= 0) it.getLong(dateIndex) else 0L
                val duration = if (durationIndex >= 0) it.getString(durationIndex) ?: "0" else "0"
                val cachedName = if (nameIndex >= 0) it.getString(nameIndex) ?: "เบอร์ไม่รู้จัก" else "เบอร์ไม่รู้จัก"

                val typeStr = when (typeCode) {
                    android.provider.CallLog.Calls.INCOMING_TYPE -> "โทรเข้า/รับสาย"
                    android.provider.CallLog.Calls.OUTGOING_TYPE -> "โทรออก"
                    android.provider.CallLog.Calls.MISSED_TYPE -> "ไม่ได้รับสาย"
                    else -> "อื่นๆ"
                }

                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(dateMillis))

                callLogsList.add(mapOf(
                    "number" to number,
                    "name" to cachedName,
                    "type" to typeStr,
                    "date" to dateStr,
                    "duration" to "${duration}วินาที"
                ))
                count++
            }
        }
        return callLogsList
    }

    private fun logCallLogToGoogleSheetAsync(call: Map<String, String>) {
        val url = _googleSheetUrl.value
        if (url.isBlank()) return

        val payloadMap = mapOf(
            "timestamp" to call["date"],
            "customerMessage" to "[ระบบบันทึกประวัติการโทร: ${call["type"]}]",
            "aiMessage" to "เบอร์: ${call["number"]} (ชื่อ: ${call["name"]}), ระยะเวลา: ${call["duration"]}",
            "location" to "ประวัติการโทร"
        )
        val jsonPayload = gson.toJson(payloadMap)
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = jsonPayload.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { e.printStackTrace() }
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    @SuppressLint("MissingPermission")
    private fun processUserSpeech(text: String, isFromAdmin: Boolean = false) {
        val displaySender = if (isFromAdmin) "ผู้ดูแล (Admin)" else "ลูกค้า"
        _uiState.value = _uiState.value.copy(isListening = false, transcribedText = if (isFromAdmin) "[คำสั่งแอดมิน] $text" else text, aiResponse = "อุ่นใจกำลังคิด...")
        
        viewModelScope.launch {
            var locationString = "ไม่ทราบตำแหน่ง"
            
            // Check location permission
            val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            if (hasFineLocation || hasCoarseLocation) {
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            locationString = "พิกัด Latitude: ${location.latitude}, Longitude: ${location.longitude}"
                        }
                    }.addOnFailureListener {
                        // ignore
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Push call history to sheet when application starts/checks call logs
            if (text.contains("โทร") || text.contains("เบอร์") || text.contains("ประวัติ")) {
                try {
                    val logs = getDeviceCallLogs()
                    logs.forEach { logCallLogToGoogleSheetAsync(it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Gather contact information if the user mentions "โทรหา" or "โทร" to keep context payload compact
            var contactsContext = ""
            var callLogContext = ""
            if (text.contains("โทร") || text.contains("เบอร์") || text.contains("ประวัติ")) {
                val contacts = getDeviceContacts().take(80) // Limit to 80 contacts to prevent token overflow
                if (contacts.isNotEmpty()) {
                    contactsContext = "[ระบบ: รายชื่อในโทรศัพท์เครื่องนี้: " + 
                            contacts.joinToString(", ") { "${it.first}=${it.second}" } + "]"
                }

                val callLogs = getDeviceCallLogs()
                if (callLogs.isNotEmpty()) {
                    callLogContext = "[ระบบ: ประวัติการโทรล่าสุด 10 สาย: " +
                            callLogs.joinToString(", ") { "${it["name"]}(${it["number"]})-${it["type"]}-${it["date"]}" } + "]"
                }
            }

            var appsContext = ""
            if (text.contains("เปิด") || text.contains("แอป") || text.contains("แอพ") || text.contains("เล่น")) {
                val apps = getInstalledApps().take(80) // Limit to 80 apps to prevent token overflow
                if (apps.isNotEmpty()) {
                    appsContext = "[ระบบ: แอปที่ติดตั้งอยู่ในโทรศัพท์เครื่องนี้: " +
                            apps.joinToString(", ") { "${it.first}=${it.second}" } + "]"
                }
            }
            
            var enrichedText = text
            if (isFromAdmin) {
                enrichedText = "[ผู้ส่งคำสั่ง: ผู้ดูแล/แอดมิน]\n$enrichedText"
            }
            if (contactsContext.isNotBlank()) enrichedText = "$contactsContext\n$enrichedText"
            if (callLogContext.isNotBlank()) enrichedText = "$callLogContext\n$enrichedText"
            if (appsContext.isNotBlank()) enrichedText = "$appsContext\n$enrichedText"
            
            var response = ""
            val apiKeys = getGroqApiKeys()
            val localResponse = tryLocalCommandFallback(text)
            if (localResponse != null) {
                response = localResponse
            } else if (apiKeys.isNotEmpty()) {
                response = repository.getReplyFromNongKanvela(enrichedText, locationString, "", apiKeys)
                if (response.contains("API ขัดข้อง") || response.isBlank()) {
                    response = "ขออภัยค่ะ API ขัดข้องในขณะนี้ กรุณาลองใหม่อีกครั้งค่ะ"
                }
            } else {
                response = "ขออภัยค่ะ อุ่นใจยังไม่รองรับคำถามทั่วไปนี้ หากต้องการคุยกับอุ่นใจแบบอัจฉริยะ กรุณาใส่คีย์ Groq API ในหน้าตั้งค่าบนเครื่องก่อนนะคะ"
            }
            
            // Record User Message (Save clean user text without system context injected)
            val currentList = _conversationHistory.value.toMutableList()
            currentList.add(ChatLog(sender = displaySender, message = text, location = locationString))
            _conversationHistory.value = currentList
            saveHistoryToDisk(currentList)
            
            handleAiResponse(response, displaySender)
        }
    }

    private fun handleAiResponse(response: String, userSender: String) {
        var finalResponse = response
        var actionIntent: Intent? = null

        // Check for specific actions requested by the AI
        if (response.contains("[ACTION:OPEN_APP:")) {
            val startIndex = response.indexOf("[ACTION:OPEN_APP:") + 17
            val endIndex = response.indexOf("]", startIndex)
            if (startIndex in 17 until endIndex) {
                val packageName = response.substring(startIndex, endIndex).trim()
                finalResponse = response.substring(0, response.indexOf("[ACTION:OPEN_APP:")) + 
                                response.substring(endIndex + 1)
                finalResponse = finalResponse.trim()
                
                actionIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            }
        } else if (response.contains("[ACTION:FLASHLIGHT_ON]")) {
            finalResponse = response.replace("[ACTION:FLASHLIGHT_ON]", "").trim()
            setFlashlightState(true)
        } else if (response.contains("[ACTION:FLASHLIGHT_OFF]")) {
            finalResponse = response.replace("[ACTION:FLASHLIGHT_OFF]", "").trim()
            setFlashlightState(false)
        } else if (response.contains("[ACTION:YOUTUBE]")) {
            finalResponse = response.replace("[ACTION:YOUTUBE]", "").trim()
            actionIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
        } else if (response.contains("[ACTION:FACEBOOK]")) {
            finalResponse = response.replace("[ACTION:FACEBOOK]", "").trim()
            actionIntent = context.packageManager.getLaunchIntentForPackage("com.facebook.katana")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com"))
        } else if (response.contains("[ACTION:LINE]")) {
            finalResponse = response.replace("[ACTION:LINE]", "").trim()
            actionIntent = context.packageManager.getLaunchIntentForPackage("com.linecorp.line.android")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://line.me"))
        } else if (response.contains("[ACTION:CAMERA]")) {
            finalResponse = response.replace("[ACTION:CAMERA]", "").trim()
            actionIntent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else if (response.contains("[ACTION:CALL:")) {
            // Extract the phone number, e.g. [ACTION:CALL:0812345678]
            val startIndex = response.indexOf("[ACTION:CALL:") + 13
            val endIndex = response.indexOf("]", startIndex)
            if (startIndex in 13 until endIndex) {
                val phoneNumber = response.substring(startIndex, endIndex).trim()
                finalResponse = response.substring(0, response.indexOf("[ACTION:CALL:")) + 
                                response.substring(endIndex + 1)
                finalResponse = finalResponse.trim()
                
                actionIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        } else if (response.contains("[ACTION:VOLUME_ON]")) {
            finalResponse = response.replace("[ACTION:VOLUME_ON]", "").trim()
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.ringerMode = android.media.AudioManager.RINGER_MODE_NORMAL
            } catch (e: Exception) { e.printStackTrace() }
        } else if (response.contains("[ACTION:VOLUME_OFF]")) {
            finalResponse = response.replace("[ACTION:VOLUME_OFF]", "").trim()
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.ringerMode = android.media.AudioManager.RINGER_MODE_SILENT
            } catch (e: Exception) { e.printStackTrace() }
        } else if (response.contains("[ACTION:VOLUME_UP]")) {
            finalResponse = response.replace("[ACTION:VOLUME_UP]", "").trim()
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_RING, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
            } catch (e: Exception) { e.printStackTrace() }
        } else if (response.contains("[ACTION:VOLUME_DOWN]")) {
            finalResponse = response.replace("[ACTION:VOLUME_DOWN]", "").trim()
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_RING, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
            } catch (e: Exception) { e.printStackTrace() }
        } else if (response.contains("[ACTION:VIBRATE_MODE]")) {
            finalResponse = response.replace("[ACTION:VIBRATE_MODE]", "").trim()
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.ringerMode = android.media.AudioManager.RINGER_MODE_VIBRATE
            } catch (e: Exception) { e.printStackTrace() }
        } else if (response.contains("[ACTION:SET_ALARM:")) {
            val startIndex = response.indexOf("[ACTION:SET_ALARM:") + 18
            val endIndex = response.indexOf("]", startIndex)
            if (startIndex in 18 until endIndex) {
                val parts = response.substring(startIndex, endIndex).split(":")
                if (parts.size >= 2) {
                    val hour = parts[0].toIntOrNull()
                    val minute = parts[1].toIntOrNull()
                    if (hour != null && minute != null) {
                        finalResponse = response.substring(0, response.indexOf("[ACTION:SET_ALARM:")) + 
                                        response.substring(endIndex + 1)
                        finalResponse = finalResponse.trim()
                        actionIntent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                }
            }
        } else if (response.contains("[ACTION:SET_REMINDER:")) {
            val startIndex = response.indexOf("[ACTION:SET_REMINDER:") + 21
            val endIndex = response.indexOf("]", startIndex)
            if (startIndex in 21 until endIndex) {
                val paramsStr = response.substring(startIndex, endIndex)
                val parts = paramsStr.split("|")
                if (parts.size >= 4) {
                    val kind = parts[0]
                    val repeatInterval = parts[1].toLongOrNull() ?: 0L
                    val triggerAtMillis = parts[2].toLongOrNull() ?: 0L
                    val encodedTitle = parts[3]
                    val decodedTitle = try {
                        java.net.URLDecoder.decode(encodedTitle, "UTF-8")
                    } catch (e: Exception) {
                        encodedTitle
                    }
                    
                    finalResponse = response.substring(0, response.indexOf("[ACTION:SET_REMINDER:")) + 
                                    response.substring(endIndex + 1)
                    finalResponse = finalResponse.trim()
                    
                    val reminderId = java.util.UUID.randomUUID().toString()
                    val reminder = ReminderItem(
                        id = reminderId,
                        title = decodedTitle,
                        kind = kind,
                        triggerAtMillis = triggerAtMillis,
                        repeatIntervalMillis = repeatInterval,
                        enabled = true
                    )
                    
                    viewModelScope.launch(Dispatchers.IO) {
                        com.example.nongkanvelaassistant.data.ReminderStorage.upsert(context, reminder)
                        com.example.nongkanvelaassistant.data.ReminderScheduler.schedule(context, reminder)
                        loadReminders()
                    }
                }
            }
        } else if (response.contains("[ACTION:ASK_CLARIFICATION:")) {
            val startIndex = response.indexOf("[ACTION:ASK_CLARIFICATION:")
            val endIndex = response.indexOf("]", startIndex)
            if (endIndex != -1) {
                finalResponse = response.substring(0, startIndex) + response.substring(endIndex + 1)
                finalResponse = finalResponse.trim()
            }
        } else if (response.contains("[ACTION:IMPORT_CONTACTS_FROM_SHEET]")) {
            finalResponse = response.replace("[ACTION:IMPORT_CONTACTS_FROM_SHEET]", "").trim()
        }

        _uiState.value = _uiState.value.copy(aiResponse = finalResponse, actionIntent = actionIntent)
        
        // Record AI Message
        val currentList = _conversationHistory.value.toMutableList()
        currentList.add(ChatLog(sender = "อุ่นใจ", message = finalResponse))
        _conversationHistory.value = currentList
        saveHistoryToDisk(currentList)
        
        // Send to LINE if token exists
        val lastUserMessage = currentList.findLast { it.sender == userSender }?.message ?: "ไม่ทราบ"
        val lastLocation = currentList.findLast { it.sender == userSender }?.location ?: "ไม่ทราบ"
        sendLinePushMessage(lastUserMessage, finalResponse, lastLocation)
        sendGoogleSheetMessage(lastUserMessage, finalResponse, lastLocation)
        
        // Speak the response
        textToSpeech?.speak(finalResponse, TextToSpeech.QUEUE_FLUSH, null, "AiResponse")
    }

    fun clearActionIntent() {
        _uiState.value = _uiState.value.copy(actionIntent = null)
    }

    // --- RecognitionListener Callbacks ---
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    
    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match found. Please try speaking again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "Error from server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Didn't understand, please try again."
        }
        _uiState.value = _uiState.value.copy(isListening = false, transcribedText = "ข้อผิดพลาด: $errorMessage")
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            processUserSpeech(matches[0])
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onCleared() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onCleared()
    }
}

data class VoiceAssistantState(
    val isListening: Boolean = false,
    val transcribedText: String = "",
    val aiResponse: String = "สวัสดีค่ะ อุ่นใจพร้อมดูแลและช่วยเหลือแล้วค่ะ",
    val errorMessage: String? = null,
    val actionIntent: Intent? = null
)
