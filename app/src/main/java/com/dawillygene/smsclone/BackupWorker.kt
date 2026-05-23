package com.dawillygene.smsclone

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    
    private val CHANNEL_ID = "sms_backup_channel"
    private val NOTIFICATION_ID = 1

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            showNotification("Syncing Messages", "The backup process has started...")
            
            val prefs = applicationContext.getSharedPreferences("SmsClonePrefs", Context.MODE_PRIVATE)
            val savedPath = prefs.getString("export_path", null)
            
            val repo = SmsRepository(applicationContext)
            val messages = repo.fetchAllSms()
            val conversations = repo.buildConversations(messages)
            val result = repo.exportToJson(conversations, savedPath)
            
            if (result.success) {
                showNotification("Sync Complete", "Successfully backed up ${result.count} conversations.")
                Result.success()
            } else {
                showNotification("Sync Failed", result.message)
                Result.failure()
            }
        } catch (e: Exception) {
            showNotification("Sync Error", e.message ?: "Unknown error occurred")
            Result.failure()
        }
    }

    private fun showNotification(title: String, content: String) {
        createNotificationChannel()
        
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.dawillygene) // Using the logo
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SMS Backup"
            val descriptionText = "Notifications for background SMS sync"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
