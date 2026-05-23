package com.dawillygene.smsclone.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.dawillygene.smsclone.R
import com.dawillygene.smsclone.data.model.*
import com.dawillygene.smsclone.data.repository.ForensicRepository
import com.dawillygene.smsclone.data.repository.SmsRepository
import com.dawillygene.smsclone.service.SmsMonitorService
import com.dawillygene.smsclone.worker.BackupWorker
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var btnExport: MaterialButton
    private lateinit var btnForensicExport: MaterialButton
    private lateinit var btnSelectPath: MaterialButton
    private lateinit var switchAutoBackup: MaterialSwitch
    private lateinit var tvStatus: TextView
    private lateinit var progressIndicator: LinearProgressIndicator

    private lateinit var cbCallLogs: CheckBox
    private lateinit var cbContacts: CheckBox
    private lateinit var cbCalendar: CheckBox
    private lateinit var cbBrowser: CheckBox
    private lateinit var cbMediaExif: CheckBox
    private lateinit var cbAppUsage: CheckBox

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

        initViews()
        
        val savedPath = getSavedExportPath()
        updatePathButtonText(savedPath)

        btnExport.setOnClickListener { checkAndRequestSmsPermissions() }
        btnForensicExport.setOnClickListener { checkAndRequestForensicPermissions() }
        btnSelectPath.setOnClickListener { folderPickerLauncher.launch(null) }

        switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) scheduleAutoBackup() else cancelAutoBackup()
        }
    }

    private fun initViews() {
        btnExport = findViewById(R.id.btnExport)
        btnForensicExport = findViewById(R.id.btnForensicExport)
        btnSelectPath = findViewById(R.id.btnSelectPath)
        switchAutoBackup = findViewById(R.id.switchAutoBackup)
        tvStatus = findViewById(R.id.tvStatus)
        progressIndicator = findViewById(R.id.progressIndicator)

        cbCallLogs = findViewById(R.id.cbCallLogs)
        cbContacts = findViewById(R.id.cbContacts)
        cbCalendar = findViewById(R.id.cbCalendar)
        cbBrowser = findViewById(R.id.cbBrowser)
        cbMediaExif = findViewById(R.id.cbMediaExif)
        cbAppUsage = findViewById(R.id.cbAppUsage)
    }

    private fun checkAndRequestSmsPermissions() {
        val permissions = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 123)
        } else {
            exportSmsMessages()
        }
    }

    private fun checkAndRequestForensicPermissions() {
        val permissions = mutableListOf<String>()
        if (cbCallLogs.isChecked) permissions.add(Manifest.permission.READ_CALL_LOG)
        if (cbContacts.isChecked) permissions.add(Manifest.permission.READ_CONTACTS)
        if (cbCalendar.isChecked) permissions.add(Manifest.permission.READ_CALENDAR)
        if (cbMediaExif.isChecked) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        }

        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        
        if (cbAppUsage.isChecked && !hasUsageStatsPermission()) {
            tvStatus.text = "Please enable Usage Stats in Settings"
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 124)
        } else {
            runForensicAcquisition()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (requestCode == 123) exportSmsMessages()
            if (requestCode == 124) runForensicAcquisition()
        } else {
            tvStatus.text = "Permissions denied."
        }
    }

    private fun exportSmsMessages() {
        startSmsMonitorService()
        tvStatus.text = "Initializing SMS export..."
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

    private fun runForensicAcquisition() {
        tvStatus.text = "Starting Forensic Acquisition..."
        progressIndicator.visibility = View.VISIBLE
        btnForensicExport.isEnabled = false

        lifecycleScope.launch {
            val savedPath = getSavedExportPath()
            val resultPath = withContext(Dispatchers.IO) {
                val repo = ForensicRepository(this@MainActivity)
                val files = mutableListOf<File>()

                if (cbCallLogs.isChecked) repo.saveForensicData("call_logs", repo.extractCallLogs())?.let { files.add(it) }
                if (cbContacts.isChecked) repo.saveForensicData("contacts", repo.extractContacts())?.let { files.add(it) }
                if (cbCalendar.isChecked) repo.saveForensicData("calendar", repo.extractCalendarEvents())?.let { files.add(it) }
                if (cbBrowser.isChecked) repo.saveForensicData("browser_history", repo.extractBrowserHistory())?.let { files.add(it) }
                if (cbMediaExif.isChecked) repo.saveForensicData("media_metadata", repo.extractMediaExif())?.let { files.add(it) }
                if (cbAppUsage.isChecked) repo.saveForensicData("app_usage", repo.extractAppUsage(7))?.let { files.add(it) }

                val manifestFile = repo.generateManifest(files)
                manifestFile?.let { 
                    files.add(it) 
                    val manifest = GsonBuilder().create().fromJson(it.readText(), ForensicManifest::class.java)
                    repo.generateHtmlReport(manifest)?.let { report -> files.add(report) }
                }

                repo.createForensicArchive(files, savedPath)
            }

            progressIndicator.visibility = View.GONE
            btnForensicExport.isEnabled = true
            tvStatus.text = if (resultPath != null) "Success: $resultPath" else "Acquisition Failed"
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
            btnSelectPath.text = "STORAGE: ${uri.lastPathSegment ?: "SELECTED"}"
        } else {
            btnSelectPath.text = "STORAGE PATH: NOT SET"
        }
    }

    private fun scheduleAutoBackup() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val dailyRequest = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).setConstraints(constraints).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("sms_backup_work", ExistingPeriodicWorkPolicy.KEEP, dailyRequest)
        tvStatus.text = "Auto-backup scheduled daily."
    }

    private fun cancelAutoBackup() {
        WorkManager.getInstance(this).cancelUniqueWork("sms_backup_work")
        tvStatus.text = "Auto-backup cancelled."
    }
}
