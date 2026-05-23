package com.dawillygene.smsclone.data.repository

import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.dawillygene.smsclone.data.model.*
import com.google.gson.GsonBuilder
import java.io.*
import java.security.MessageDigest
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ForensicRepository(private val context: Context) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun extractCallLogs(): List<CallLogEntry> {
        val list = mutableListOf<CallLogEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )
        context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(CallLogEntry(
                    id = cursor.getLong(0),
                    number = cursor.getString(1) ?: "",
                    contactName = cursor.getString(2),
                    date = cursor.getLong(3),
                    duration = cursor.getLong(4),
                    type = cursor.getInt(5)
                ))
            }
        }
        return list
    }

    fun extractContacts(): List<ContactEntry> {
        val contacts = mutableMapOf<Long, ContactEntry>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1)
                val number = cursor.getString(2) ?: ""
                val existing = contacts[id]
                if (existing == null) {
                    contacts[id] = ContactEntry(id, name, mutableListOf(number), mutableListOf(), null)
                } else {
                    (existing.numbers as MutableList).add(number)
                }
            }
        }
        return contacts.values.toList()
    }

    fun extractCalendarEvents(): List<CalendarEventEntry> {
        val list = mutableListOf<CalendarEventEntry>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        )
        context.contentResolver.query(CalendarContract.Events.CONTENT_URI, projection, null, null, "${CalendarContract.Events.DTSTART} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(CalendarEventEntry(
                    id = cursor.getLong(0),
                    calendarName = cursor.getString(1),
                    title = cursor.getString(2),
                    description = cursor.getString(3),
                    location = cursor.getString(4),
                    startTime = cursor.getLong(5),
                    endTime = cursor.getLong(6)
                ))
            }
        }
        return list
    }

    fun extractBrowserHistory(): List<BrowserEntry> {
        val list = mutableListOf<BrowserEntry>()
        val BOOKMARKS_URI = Uri.parse("content://browser/bookmarks")
        val projection = arrayOf("title", "url", "date", "visits")
        try {
            context.contentResolver.query(BOOKMARKS_URI, projection, null, null, "date DESC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(BrowserEntry(
                        title = cursor.getString(0),
                        url = cursor.getString(1),
                        date = cursor.getLong(2),
                        visits = cursor.getInt(3)
                    ))
                }
            }
        } catch (e: Exception) { }
        return list
    }

    fun extractMediaExif(): List<MediaMetadata> {
        val list = mutableListOf<MediaMetadata>()
        val projection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN
        )
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: "unknown"
                val dateTaken = cursor.getLong(2)
                try {
                    val exif = ExifInterface(path)
                    val latLong = FloatArray(2)
                    val hasGps = exif.getLatLong(latLong)
                    list.add(MediaMetadata(
                        fileName = name,
                        path = path,
                        dateTaken = if (dateTaken > 0) dateTaken else null,
                        latitude = if (hasGps) latLong[0].toDouble() else null,
                        longitude = if (hasGps) latLong[1].toDouble() else null,
                        make = exif.getAttribute(ExifInterface.TAG_MAKE),
                        model = exif.getAttribute(ExifInterface.TAG_MODEL),
                        software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
                    ))
                } catch (e: Exception) { }
            }
        }
        return list
    }

    fun extractAppUsage(days: Int): List<AppUsageEntry> {
        val list = mutableListOf<AppUsageEntry>()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (days.toLong() * 24 * 60 * 60 * 1000)
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        stats?.forEach { usage ->
            if (usage.totalTimeInForeground > 0) {
                list.add(AppUsageEntry(
                    packageName = usage.packageName,
                    appName = getAppName(usage.packageName),
                    totalTimeInForeground = usage.totalTimeInForeground,
                    lastTimeUsed = usage.lastTimeUsed
                ))
            }
        }
        return list
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    fun saveForensicData(fileName: String, data: Any): File? {
        val exportDir = context.getExternalFilesDir("forensic_temp")
        if (exportDir?.exists() == false) exportDir.mkdirs()
        val file = File(exportDir, "$fileName.json")
        try {
            file.writeText(gson.toJson(data))
            return file
        } catch (e: Exception) {
            return null
        }
    }

    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun generateManifest(files: List<File>): File? {
        val integrityList = files.map { FileIntegrity(it.name, calculateSHA256(it)) }
        val manifest = ForensicManifest(
            timestamp = System.currentTimeMillis(),
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            files = integrityList
        )
        return saveForensicData("forensic_manifest", manifest)
    }

    fun createForensicArchive(files: List<File>, targetUriString: String? = null): String? {
        val tempZip = File(context.cacheDir, "temp_forensic_${System.currentTimeMillis()}.zip")
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { out ->
                files.forEach { file ->
                    FileInputStream(file).use { fi ->
                        out.putNextEntry(ZipEntry(file.name))
                        fi.copyTo(out)
                        out.closeEntry()
                    }
                }
            }

            if (!targetUriString.isNullOrEmpty()) {
                val targetUri = Uri.parse(targetUriString)
                val rootDoc = DocumentFile.fromTreeUri(context, targetUri)
                if (rootDoc != null && rootDoc.canWrite()) {
                    val archiveName = "forensic_snapshot_${System.currentTimeMillis()}.zip"
                    val fileDoc = rootDoc.createFile("application/zip", archiveName)
                    fileDoc?.uri?.let { uri ->
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            tempZip.inputStream().use { input -> input.copyTo(output) }
                        }
                        tempZip.delete()
                        return archiveName
                    }
                }
            }

            // Fallback to internal if no target path is set
            val exportDir = context.getExternalFilesDir("forensic_exports")
            if (exportDir?.exists() == false) exportDir.mkdirs()
            val finalFile = File(exportDir, tempZip.name)
            tempZip.renameTo(finalFile)
            return finalFile.absolutePath

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun generateHtmlReport(manifest: ForensicManifest): File? {
        val html = StringBuilder()
        html.append("<html><head><title>Forensic Report</title>")
        html.append("<style>body{font-family:sans-serif;background:#f4f4f4;padding:20px;}")
        html.append(".card{background:white;padding:20px;border-radius:8px;box-shadow:0 2px 4px rgba(0,0,0,0.1);margin-bottom:20px;}")
        html.append("h1{color:#062323;} table{width:100%;border-collapse:collapse;} th,td{border:1px solid #ddd;padding:8px;text-align:left;}</style>")
        html.append("</head><body>")
        html.append("<div class='card'><h1>Forensic Case Summary</h1>")
        html.append("<p><strong>Device:</strong> ${manifest.deviceModel}</p>")
        html.append("<p><strong>OS:</strong> Android ${manifest.androidVersion}</p>")
        html.append("<p><strong>Date:</strong> ${Date(manifest.timestamp)}</p>")
        html.append("<p><strong>Root Status:</strong> ${if (isRooted()) "ROOTED" else "Non-Root"}</p></div>")
        
        html.append("<div class='card'><h2>Integrity Manifest (Chain of Custody)</h2>")
        html.append("<table><tr><th>File Name</th><th>SHA-256 Hash</th></tr>")
        manifest.files.forEach { 
            html.append("<tr><td>${it.fileName}</td><td><code>${it.sha256}</code></td></tr>")
        }
        html.append("</table></div></body></html>")

        val exportDir = context.getExternalFilesDir("forensic_temp")
        val reportFile = File(exportDir, "forensic_report.html")
        reportFile.writeText(html.toString())
        return reportFile
    }

    private fun isRooted(): Boolean {
        val paths = arrayOf("/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su")
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }
}
