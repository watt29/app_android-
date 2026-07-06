package com.example.nongkanvelaassistant.data

import android.content.Context
import com.example.nongkanvelaassistant.ui.ReminderItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ReminderStorage {
    private const val PREFS_NAME = "nongkanvela_prefs"
    private const val KEY_REMINDERS = "reminders_json"
    private val gson = Gson()

    fun load(context: Context): MutableList<ReminderItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_REMINDERS, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<ReminderItem>>() {}.type
            gson.fromJson<MutableList<ReminderItem>>(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun save(context: Context, reminders: List<ReminderItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_REMINDERS, gson.toJson(reminders)).apply()
    }

    fun upsert(context: Context, reminder: ReminderItem) {
        val reminders = load(context)
        val index = reminders.indexOfFirst { it.id == reminder.id }
        if (index >= 0) {
            reminders[index] = reminder
        } else {
            reminders.add(reminder)
        }
        save(context, reminders)
    }

    fun remove(context: Context, reminderId: String) {
        val reminders = load(context)
        if (reminders.removeAll { it.id == reminderId }) {
            save(context, reminders)
        }
    }

    fun find(context: Context, reminderId: String): ReminderItem? {
        return load(context).firstOrNull { it.id == reminderId }
    }
}
