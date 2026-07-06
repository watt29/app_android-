package com.example.nongkanvelaassistant.ui

data class ReminderItem(
    val id: String,
    val title: String,
    val kind: String,
    val triggerAtMillis: Long,
    val repeatIntervalMillis: Long = 0L,
    val enabled: Boolean = true
)
