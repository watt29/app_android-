package com.example.nongkanvelaassistant.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.nongkanvelaassistant.MainActivity
import com.example.nongkanvelaassistant.R
import com.example.nongkanvelaassistant.data.ReminderScheduler
import com.example.nongkanvelaassistant.data.ReminderStorage
import com.example.nongkanvelaassistant.ui.ReminderItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val reminderId = intent?.getStringExtra(EXTRA_REMINDER_ID)
        Log.i("ReminderAlarmReceiver", "onReceive triggered with reminderId: $reminderId")
        if (reminderId == null) return
        val reminder = ReminderStorage.find(context, reminderId)
        if (reminder == null) {
            Log.i("ReminderAlarmReceiver", "Reminder not found in storage for ID: $reminderId")
            return
        }
        Log.i("ReminderAlarmReceiver", "Found reminder: ${reminder.title}, kind: ${reminder.kind}")

        ensureChannel(context)

        val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reminder.triggerAtMillis))
        val title = when (reminder.kind) {
            "medicine" -> "ถึงเวลากินยาแล้วค่ะ"
            "appointment" -> "ถึงเวลานัดหมายแล้วค่ะ"
            "vaccine" -> "ถึงเวลาฉีดวัคซีนแล้วค่ะ"
            else -> "ถึงเวลาเตือนแล้วค่ะ"
        }
        val body = "${reminder.title} เวลา $formattedTime"

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentIntent = android.app.PendingIntent.getActivity(
            context,
            reminder.id.hashCode() + 9,
            openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)

        // Asynchronously play alarm sound and speak the notification using TTS
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                playAlarmAndSpeak(context, "เตือน ${reminder.title}")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }

        if (reminder.repeatIntervalMillis > 0L) {
            val nextReminder = reminder.copy(
                triggerAtMillis = reminder.triggerAtMillis + reminder.repeatIntervalMillis
            )
            ReminderStorage.upsert(context, nextReminder)
            ReminderScheduler.schedule(context, nextReminder)
        } else {
            ReminderStorage.remove(context, reminder.id)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun playAlarmAndSpeak(context: Context, speechText: String) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "NongKanvela:AlarmWakeLock"
        )
        
        try {
            // Acquire wake lock for max 35 seconds
            wakeLock.acquire(35000)
            Log.i("ReminderAlarmReceiver", "WakeLock acquired successfully")

            // Play siren/alarm sound for 3 seconds
            var mediaPlayer: MediaPlayer? = null
            try {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                Log.i("ReminderAlarmReceiver", "Playing alarm sound from Uri: $alarmUri")
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = false
                    prepare()
                    start()
                }
                delay(3000) // Play siren for 3 seconds
            } catch (e: Exception) {
                Log.e("ReminderAlarmReceiver", "Error playing alarm sound", e)
            } finally {
                try {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Speak using TextToSpeech (Repeat 2 times with 3 seconds delay in between)
            for (i in 1..3) {
                Log.i("ReminderAlarmReceiver", "Speaking text (attempt $i): $speechText")
                speakText(context, speechText)
                if (i < 3) {
                    delay(3000)
                }
            }
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    @Suppress("UNNECESSARY_SAFE_CALL", "SENSELESS_COMPARISON")
    private suspend fun speakText(context: Context, text: String) = suspendCancellableCoroutine<Unit> { continuation ->
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            val localTts = tts
            if (status == TextToSpeech.SUCCESS && localTts != null) {
                val result = localTts.setLanguage(Locale.forLanguageTag("th"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    continuation.resume(Unit)
                    localTts.shutdown()
                } else {
                    localTts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (continuation.isActive) continuation.resume(Unit)
                            localTts.shutdown()
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (continuation.isActive) continuation.resume(Unit)
                            localTts.shutdown()
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (continuation.isActive) continuation.resume(Unit)
                            localTts.shutdown()
                        }
                    })
                    val params = Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "AlarmSpeak")
                    }
                    localTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "AlarmSpeak")
                }
            } else {
                continuation.resume(Unit)
                localTts?.shutdown()
            }
        }
        continuation.invokeOnCancellation {
            try {
                val activeTts = tts
                if (activeTts != null) {
                    activeTts.stop()
                    activeTts.shutdown()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Care Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "แจ้งเตือนกินยา นัดหมอ และวัคซีน"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "care_reminders"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }
}
