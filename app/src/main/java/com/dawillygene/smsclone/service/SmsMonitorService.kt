package com.dawillygene.smsclone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.dawillygene.smsclone.R
import com.dawillygene.smsclone.data.local.SmsShadow
import com.dawillygene.smsclone.data.local.SmsShadowDao
import com.dawillygene.smsclone.data.local.ShadowDatabase
import com.dawillygene.smsclone.data.model.SmsMessage
import com.dawillygene.smsclone.data.repository.SmsRepository
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class SmsMonitorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var observer: ContentObserver
    private lateinit var shadowDao: SmsShadowDao
    private val gson = Gson()

    private val CHANNEL_ID = "sms_monitor_channel"
    private val NOTIFICATION_ID = 99

    override fun onCreate() {
        super.onCreate()
        shadowDao = ShadowDatabase.getDatabase(this).smsShadowDao()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scope.launch {
                    detectAndLogChanges()
                }
            }
        }

        contentResolver.registerContentObserver(
            Uri.parse("content://sms"),
            true,
            observer
        )

        scope.launch {
            detectAndLogChanges(isInitial = true)
        }
    }

    private suspend fun detectAndLogChanges(isInitial: Boolean = false) {
        val repo = SmsRepository(this)
        val currentSms = repo.fetchAllSms()
        val shadowSms = shadowDao.getAll()

        val currentMap = currentSms.associateBy { it.id }
        val shadowMap = shadowSms.associateBy { it.id }

        if (isInitial) {
            val entities = currentSms.map { it.toShadow() }
            shadowDao.insertAll(entities)
            return
        }

        shadowSms.forEach { shadow ->
            if (!currentMap.containsKey(shadow.id)) {
                logChange(shadow.toSmsMessage(), "deleted")
                shadowDao.delete(shadow.id)
            }
        }

        currentSms.forEach { current ->
            val shadow = shadowMap[current.id]
            if (shadow == null) {
                logChange(current, "inserted")
                shadowDao.insert(current.toShadow())
            } else if (current.body != shadow.body || current.type != shadow.type) {
                logChange(current, "updated")
                shadowDao.insert(current.toShadow())
            }
        }
    }

    private fun logChange(sms: SmsMessage, type: String) {
        val entry = mapOf(
            "operation" to type,
            "timestamp_logged" to System.currentTimeMillis(),
            "message" to sms
        )
        val jsonLine = gson.toJson(entry) + "\n"
        
        val logFile = File(getExternalFilesDir(null), "deleted_safety_net.jsonl")
        FileOutputStream(logFile, true).use {
            it.write(jsonLine.toByteArray())
        }

        val prefs = getSharedPreferences("SmsClonePrefs", MODE_PRIVATE)
        val savedPath = prefs.getString("export_path", null)
        if (savedPath != null) {
            try {
                val rootDoc = DocumentFile.fromTreeUri(this, Uri.parse(savedPath))
                if (rootDoc != null && rootDoc.canWrite()) {
                    val fileDoc = rootDoc.findFile("deleted_safety_net.jsonl") 
                        ?: rootDoc.createFile("application/x-jsonlines", "deleted_safety_net.jsonl")
                    
                    fileDoc?.uri?.let { uri ->
                        contentResolver.openOutputStream(uri, "wa")?.use { out ->
                            out.write(jsonLine.toByteArray())
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SMS Monitor"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Safety Net Active")
            .setContentText("Monitoring for message changes...")
            .setSmallIcon(R.drawable.dawillygene)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        job.cancel()
        super.onDestroy()
    }

    private fun SmsMessage.toShadow() = SmsShadow(id, address, body, date, type, threadId)
    private fun SmsShadow.toSmsMessage() = SmsMessage(id, address, body, date, type, threadId)
}
