package com.example.nongkanvelaassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.nongkanvelaassistant.MainActivity
import java.util.Locale
import kotlin.math.sqrt

class EmergencyService : Service(), SensorEventListener, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "EmergencyService"
        private const val CHANNEL_ID = "emergency_service_channel"
        private const val NOTIFICATION_ID = 9999
        
        const val ACTION_SOS_TRIGGERED = "com.example.nongkanvelaassistant.ACTION_SOS_TRIGGERED"
        const val EXTRA_G_FORCE = "extra_g_force"
        
        // G-force threshold for fall/impact detection (2.8g is standard for falls)
        private const val DEFAULT_THRESHOLD = 2.8f
        private const val COOLDOWN_MS = 8000L
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastTriggerTime = 0L
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating EmergencyService")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        if (accelerometer != null) {
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
        Log.d(TAG, "EmergencyService onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
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
        // 1. Play TTS Warning
        speakAlert()

        // 2. Broadcast to MainActivity (to show countdown dialog)
        val broadcastIntent = Intent(ACTION_SOS_TRIGGERED).apply {
            putExtra(EXTRA_G_FORCE, gForce)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)

        // 3. Launch MainActivity in case it is closed
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_SOS_TRIGGERED
            putExtra(EXTRA_G_FORCE, gForce)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        
        try {
            startActivity(activityIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start MainActivity from background: ${e.message}")
        }
    }

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

    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("อุ่นใจเฝ้าระวังภัย")
            .setContentText("ระบบตรวจจับการล้มและการกระแทกทำงานอยู่เบื้องหลัง")
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
