package com.example.nongkanvelaassistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.nongkanvelaassistant.data.ReminderScheduler
import com.example.nongkanvelaassistant.data.ReminderStorage
import com.example.nongkanvelaassistant.service.EmergencyService

class ReminderBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "Received: ${intent.action} — starting EmergencyService and rescheduling reminders")

                // 1. Start EmergencyService (SOS / fall detection) without opening the main screen
                startEmergencyService(context)

                // 2. Reschedule all active reminders (existing behaviour preserved)
                rescheduleReminders(context)
            }
            else -> {
                Log.w(TAG, "Unexpected action: ${intent?.action}")
            }
        }
    }

    private fun startEmergencyService(context: Context) {
        try {
            val serviceIntent = Intent(context, EmergencyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "EmergencyService started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start EmergencyService: ${e.message}")
        }
    }

    private fun rescheduleReminders(context: Context) {
        try {
            val reminders = ReminderStorage.load(context)
            reminders.filter { it.enabled }.forEach { reminder ->
                ReminderScheduler.schedule(context, reminder)
            }
            Log.d(TAG, "Rescheduled ${reminders.count { it.enabled }} reminder(s)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule reminders: ${e.message}")
        }
    }
}
