package com.dawillygene.smsclone.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dawillygene.smsclone.R
import com.dawillygene.smsclone.data.model.ExportResult
import com.dawillygene.smsclone.data.repository.SmsRepository
import com.dawillygene.smsclone.service.SmsMonitorService
import com.dawillygene.smsclone.worker.BackupWorker
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var btnExport: MaterialButton
    private lateinit var btnSelectPath: MaterialButton
    private lateinit var switchAutoBackup: MaterialSwitch
    private lateinit var tvStatus: TextView
    private lateinit var progressIndicator: LinearProgressIndicator

    private val PREFS_NAME = "SmsClonePrefs"
    private val KEY_EXPORT_PATH = "export_path"

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            saveExportPath(it.toString())
            updatePathButtonText(it.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnExport = findViewById(R.id.btnExport)
        btnSelectPath = findViewById(R.id.btnSelectPath)
        switchAutoBackup = findViewById(R.id.switchAutoBackup)
        tvStatus = findViewById(R.id.tvStatus)
        progressIndicator = findViewById(R.id.progressIndicator)

        val savedPath = getSavedExportPath()
        updatePathButtonText(savedPath)

        btnExport.setOnClickListener {
            checkAndRequestPermissions()
        }

        btnSelectPath.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) scheduleAutoBackup() else cancelAutoBackup()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 123)
        } else {
            exportMessages()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startSmsMonitorService()
            exportMessages()
        } else {
            tvStatus.text = "Permissions denied."
        }
    }

    private fun exportMessages() {
        startSmsMonitorService()
        tvStatus.text = "Initializing export..."
        progressIndicator.visibility = View.VISIBLE
        btnExport.isEnabled = false

        lifecycleScope.launch {
            val savedPath = getSavedExportPath()
            val result = withContext(Dispatchers.IO) {
                try {
                    val repo = SmsRepository(this@MainActivity)
                    val allMessages = repo.fetchAllSms()
                    val conversations = repo.buildConversations(allMessages)
                    repo.exportToJson(conversations, savedPath)
                } catch (e: Exception) {
                    ExportResult(false, e.message ?: "Unknown error", 0)
                }
            }
            
            progressIndicator.visibility = View.GONE
            btnExport.isEnabled = true
            tvStatus.text = result.message
        }
    }

    private fun startSmsMonitorService() {
        val intent = Intent(this, SmsMonitorService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun saveExportPath(path: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_EXPORT_PATH, path).apply()
    }

    private fun getSavedExportPath(): String? {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_EXPORT_PATH, null)
    }

    private fun updatePathButtonText(path: String?) {
        if (path != null) {
            val uri = Uri.parse(path)
            btnSelectPath.text = "Storage: ${uri.lastPathSegment ?: "Selected Folder"}"
        } else {
            btnSelectPath.text = "Storage Path: Not set (using internal)"
        }
    }

    private fun scheduleAutoBackup() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val dailyRequest = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("sms_backup_work", ExistingPeriodicWorkPolicy.KEEP, dailyRequest)
        tvStatus.text = "Auto-backup scheduled daily."
    }

    private fun cancelAutoBackup() {
        WorkManager.getInstance(this).cancelUniqueWork("sms_backup_work")
        tvStatus.text = "Auto-backup cancelled."
    }
}
