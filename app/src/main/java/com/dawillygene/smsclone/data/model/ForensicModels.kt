package com.dawillygene.smsclone.data.model

data class CallLogEntry(
    val id: Long,
    val number: String,
    val contactName: String?,
    val date: Long,
    val duration: Long,
    val type: Int // Incoming, Outgoing, Missed, etc.
)

data class ContactEntry(
    val id: Long,
    val name: String?,
    val numbers: List<String>,
    val emails: List<String>,
    val note: String?
)

data class CalendarEventEntry(
    val id: Long,
    val calendarName: String?,
    val title: String?,
    val description: String?,
    val location: String?,
    val startTime: Long,
    val endTime: Long
)

data class BrowserEntry(
    val title: String?,
    val url: String?,
    val date: Long,
    val visits: Int
)

data class MediaMetadata(
    val fileName: String,
    val path: String,
    val dateTaken: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val make: String?,
    val model: String?,
    val software: String?
)

data class AppUsageEntry(
    val packageName: String,
    val appName: String?,
    val totalTimeInForeground: Long,
    val lastTimeUsed: Long
)

data class ForensicManifest(
    val timestamp: Long,
    val deviceModel: String,
    val androidVersion: String,
    val files: List<FileIntegrity>
)

data class FileIntegrity(
    val fileName: String,
    val sha256: String
)
