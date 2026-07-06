package com.example.nongkanvelaassistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.nongkanvelaassistant.data.ReminderScheduler
import com.example.nongkanvelaassistant.data.ReminderStorage

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val reminders = ReminderStorage.load(context)
        reminders.filter { it.enabled }.forEach { reminder ->
            ReminderScheduler.schedule(context, reminder)
        }
    }
}
