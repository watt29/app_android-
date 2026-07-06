package com.example.nongkanvelaassistant.ui

data class DeviceContact(
    val contactId: Long,
    val rawContactId: Long,
    val name: String,
    val number: String
)

data class SheetCommand(
    val command: String,
    val name: String,
    val number: String,
    val status: String
)

data class SheetCommandResponse(
    val result: String,
    val data: List<SheetCommand>?
)

data class ScamNumberEntry(
    val number: String,
    val label: String,
    val source: String,
    val note: String = "",
    val status: String = "active",
    val updatedAt: String = ""
)

data class ScamNumberResponse(
    val result: String,
    val data: List<ScamNumberEntry>?
)

data class ScamSourceReference(
    val title: String,
    val url: String,
    val note: String,
    val status: String = "active",
    val updatedAt: String = ""
)

data class ScamSourceResponse(
    val result: String,
    val data: List<ScamSourceReference>?
)

data class DeviceRegistryEntry(
    val deviceId: String,
    val deviceName: String,
    val role: String,
    val model: String = "",
    val appVersion: String = "",
    val lastSeen: String = "",
    val firstSeen: String = "",
    val status: String = "active"
)

data class DeviceRegistryResponse(
    val result: String,
    val data: List<DeviceRegistryEntry>?
)

data class GitHubDataBundle(
    val result: String? = null,
    val botCommands: List<SheetCommand>? = null,
    val contacts: List<SheetCommand>? = null,
    val scamNumbers: List<ScamNumberEntry>? = null,
    val scamSources: List<ScamSourceReference>? = null,
    val deviceRegistry: List<DeviceRegistryEntry>? = null
)
