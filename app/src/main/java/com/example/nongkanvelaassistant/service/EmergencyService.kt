package com.example.nongkanvelaassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.provider.ContactsContract
import android.net.Uri
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.core.app.NotificationCompat
import com.example.nongkanvelaassistant.MainActivity
import com.example.nongkanvelaassistant.data.TelecomCallController
import java.util.Locale
import kotlin.math.sqrt

class EmergencyService : Service(), SensorEventListener, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "EmergencyService"
        private const val CHANNEL_ID = "emergency_service_channel"
        private const val NOTIFICATION_ID = 9999
        
        const val ACTION_SOS_TRIGGERED = "com.example.nongkanvelaassistant.ACTION_SOS_TRIGGERED"
        const val ACTION_TRIGGER_SOS = "com.example.nongkanvelaassistant.ACTION_TRIGGER_SOS"
        const val ACTION_SET_ENABLED = "com.example.nongkanvelaassistant.ACTION_SET_EMERGENCY_ENABLED"
        const val EXTRA_ENABLED = "emergency_enabled"
        const val EXTRA_G_FORCE = "extra_g_force"
        
        // G-force threshold for fall/impact detection (2.8g is standard for falls)
        private const val DEFAULT_THRESHOLD = 2.8f
        private const val COOLDOWN_MS = 60_000L

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastTriggerTime = 0L
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating EmergencyService")
        if (!hasLocationPermission(this)) {
            Log.w(TAG, "Location permission is not granted; EmergencyService will not start yet")
            stopSelf()
            return
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(isEmergencyEnabled()))
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        if (accelerometer != null && isEmergencyEnabled()) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            Log.e(TAG, "Accelerometer not available on this device!")
        }

        try {
            textToSpeech = TextToSpeech(this, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER_SOS) {
            triggerSos(0.0)
        } else if (intent?.action == ACTION_SET_ENABLED) {
            val enabled = intent.getBooleanExtra(EXTRA_ENABLED, true)
            preferences().edit().putBoolean("emergency_system_enabled", enabled).apply()
            if (enabled) registerSensor() else sensorManager.unregisterListener(this)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, buildNotification(enabled))
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isEmergencyEnabled()) return
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val acceleration = sqrt((x * x + y * y + z * z).toDouble())
        val gForce = acceleration / SensorManager.GRAVITY_EARTH

        // Load sensitivity threshold from preferences if customized
        val prefs = getSharedPreferences("nongkanvela_prefs", Context.MODE_PRIVATE)
        val sensitivity = prefs.getFloat("shake_sensitivity", DEFAULT_THRESHOLD)

        if (gForce >= sensitivity) {
            val now = System.currentTimeMillis()
            if (now - lastTriggerTime > COOLDOWN_MS) {
                lastTriggerTime = now
                Log.w(TAG, "Fall or heavy shake detected! G-force: $gForce")
                triggerSos(gForce)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerSos(gForce: Double) {
        if (!isEmergencyEnabled()) return
        val target = findEmergencyContact() ?: run {
            speak("ไม่พบรายชื่อผู้ดูแล แม่ พ่อ หรือลูก สำหรับติดต่อฉุกเฉินค่ะ")
            return
        }
        vibrateSosStarted()
        speak("ตรวจพบเหตุฉุกเฉิน กำลังส่งข้อความและโทรหา ${target.first} ค่ะ")
        // Calling must never wait for a location lookup or for the vibration to end.
        placeEmergencyCall(target.second)
        val locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) sendSosLocation(target.second, location.latitude, location.longitude)
                else locationClient.lastLocation.addOnSuccessListener { last -> sendSosLocation(target.second, last?.latitude, last?.longitude) }
            }
            .addOnFailureListener {
                locationClient.lastLocation.addOnSuccessListener { last -> sendSosLocation(target.second, last?.latitude, last?.longitude) }
            }
    }

    private fun sendSosLocation(number: String, latitude: Double?, longitude: Double?) {
        val locationText = if (latitude != null && longitude != null) "https://maps.google.com/?q=$latitude,$longitude" else "ไม่สามารถระบุพิกัดได้"
        sendEmergencySms(number, "แจ้งเหตุฉุกเฉินจากอุ่นใจ: $locationText")
    }

    private fun placeEmergencyCall(number: String) {
        val telecomCallController = TelecomCallController(this)
        try {
            if (!telecomCallController.placeEmergencyCall(number)) {
                startActivity(telecomCallController.createDialFallbackIntent(number))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Emergency call screen unavailable", e)
        }
    }

    private fun vibrateSosStarted() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        val timings = longArrayOf(0, 160, 120, 160, 120, 160)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }

    private fun sendEmergencySms(number: String, message: String) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return
        try {
            val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
            val manager = if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) SmsManager.getSmsManagerForSubscriptionId(subscriptionId) else SmsManager.getDefault()
            val parts = manager.divideMessage(message)
            if (parts.size > 1) manager.sendMultipartTextMessage(number, null, parts, null, null) else manager.sendTextMessage(number, null, message, null, null)
        } catch (e: Exception) { Log.e(TAG, "Emergency SMS failed", e) }
    }

    private fun findEmergencyContact(): Pair<String, String>? {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val priorities = listOf("ผู้ดูแล", "ฉุกเฉิน", "แม่", "พ่อ", "ลูก", "emergency")
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val contacts = mutableListOf<Pair<String, String>>()
            while (cursor.moveToNext()) contacts += (cursor.getString(nameIndex) ?: "") to (cursor.getString(numberIndex) ?: "")
            for (priority in priorities) contacts.firstOrNull { it.first.contains(priority, ignoreCase = true) && it.second.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun speak(text: String) { if (isTtsReady) textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "EmergencySos") }

    private fun preferences() = getSharedPreferences("nongkanvela_prefs", Context.MODE_PRIVATE)
    private fun isEmergencyEnabled() = preferences().getBoolean("emergency_system_enabled", true)
    private fun registerSensor() { if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL) }

    private fun speakAlert() {
        if (isTtsReady) {
            try {
                textToSpeech?.speak(
                    "ตรวจพบการกระแทกหรือการล้มรุนแรง ระบบกำลังจะแจ้งเหตุฉุกเฉินค่ะ หากคุณปลอดภัยดี กรุณากดยกเลิกบนหน้าจอนะคะ",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "EmergencyFallAlert"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale("th", "TH")
            val result = textToSpeech?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Thai language is not supported for TTS in service")
            } else {
                isTtsReady = true
                Log.d(TAG, "TTS initialized successfully in EmergencyService")
            }
        } else {
            Log.e(TAG, "TTS Initialization failed in EmergencyService")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "ระบบเฝ้าระวังความปลอดภัยอุ่นใจ",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(enabled: Boolean): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("อุ่นใจเฝ้าระวังภัย")
            .setContentText(if (enabled) "ระบบฉุกเฉินเปิดอยู่: กำลังตรวจจับการล้มและการเขย่า" else "ระบบฉุกเฉินปิดอยู่")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying EmergencyService")
        sensorManager.unregisterListener(this)
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}
