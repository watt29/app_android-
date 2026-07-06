package com.example.nongkanvelaassistant.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.nongkanvelaassistant.MainActivity
import com.example.nongkanvelaassistant.receiver.ReminderAlarmReceiver
import com.example.nongkanvelaassistant.ui.ReminderItem

object ReminderScheduler {
    private const val EXTRA_REMINDER_ID = "extra_reminder_id"
    private const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
    private const val EXTRA_REMINDER_KIND = "extra_reminder_kind"
    private const val EXTRA_REMINDER_REPEAT = "extra_reminder_repeat"
    private const val EXTRA_REMINDER_TRIGGER = "extra_reminder_trigger"

    fun schedule(context: Context, reminder: ReminderItem) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_REMINDER_TITLE, reminder.title)
            putExtra(EXTRA_REMINDER_KIND, reminder.kind)
            putExtra(EXTRA_REMINDER_REPEAT, reminder.repeatIntervalMillis)
            putExtra(EXTRA_REMINDER_TRIGGER, reminder.triggerAtMillis)
        }
        val requestCode = reminder.id.hashCode()
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode,
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = PendingIntent.getActivity(
            context,
            requestCode + 1,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(reminder.triggerAtMillis, showIntent),
            operation
        )
    }

    fun cancel(context: Context, reminderId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerIntent = Intent(context, ReminderAlarmReceiver::class.java)
        val requestCode = reminderId.hashCode()
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode,
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(operation)
        operation.cancel()
    }
}
