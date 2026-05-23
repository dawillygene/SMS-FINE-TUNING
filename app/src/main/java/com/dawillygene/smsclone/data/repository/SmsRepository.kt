package com.dawillygene.smsclone.data.repository

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.documentfile.provider.DocumentFile
import com.dawillygene.smsclone.data.model.Conversation
import com.dawillygene.smsclone.data.model.ExportResult
import com.dawillygene.smsclone.data.model.MessageEntry
import com.dawillygene.smsclone.data.model.SmsMessage
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsRepository(private val context: Context) {

    fun fetchAllSms(): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val projection = arrayOf("_id", "address", "body", "date", "type", "thread_id")
        val uri = Uri.parse("content://sms")
        val cursor = context.contentResolver.query(uri, projection, null, null, "date ASC")

        cursor?.use {
            while (it.moveToNext()) {
                messages.add(
                    SmsMessage(
                        id = it.getLong(0),
                        address = it.getString(1) ?: "",
                        body = it.getString(2),
                        date = it.getLong(3),
                        type = it.getInt(4),
                        threadId = it.getLong(5)
                    )
                )
            }
        }
        return messages
    }

    fun buildConversations(messages: List<SmsMessage>): List<Conversation> {
        val contactCache = mutableMapOf<String, String>()
        val grouped = messages.groupBy { it.threadId }
        return grouped.map { (threadId, msgs) ->
            val first = msgs.first()
            val address = first.address
            val contactName = contactCache.getOrPut(address) { getContactName(address) }
            val entries = msgs.map { msg ->
                val sender = if (msg.type == 2) "me" else contactName
                MessageEntry(
                    sender = sender,
                    body = msg.body,
                    timestamp = msg.date,
                    dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(msg.date))
                )
            }
            Conversation(threadId, contactName, address, entries)
        }
    }

    private fun getContactName(phoneNumber: String): String {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0) ?: phoneNumber
            }
        }
        return phoneNumber
    }

    fun exportToJson(conversations: List<Conversation>, targetUriString: String? = null): ExportResult {
        val gson = GsonBuilder().setPrettyPrinting().create()
        var count = 0

        if (!targetUriString.isNullOrEmpty()) {
            try {
                val targetUri = Uri.parse(targetUriString)
                val rootDoc = DocumentFile.fromTreeUri(context, targetUri)
                if (rootDoc != null && rootDoc.canWrite()) {
                    conversations.forEach { conv ->
                        val safeName = conv.contactName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                        val fileName = "conv_${safeName}_${conv.threadId}.json"
                        
                        val existingFile = rootDoc.findFile(fileName)
                        val fileDoc = existingFile ?: rootDoc.createFile("application/json", fileName)
                        
                        fileDoc?.uri?.let { fileUri ->
                            context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                                outputStream.write(gson.toJson(conv).toByteArray())
                                count++
                            }
                        }
                    }
                    return ExportResult(true, "Exported $count threads to ${rootDoc.name}", count)
                }
            } catch (e: Exception) {
                return ExportResult(false, "Error: ${e.message}", 0)
            }
        }

        val exportDir = context.getExternalFilesDir("sms_backups")
        exportDir?.let { dir ->
            if (!dir.exists()) dir.mkdirs()
            conversations.forEach { conv ->
                val safeName = conv.contactName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val file = File(dir, "conv_${safeName}_${conv.threadId}.json")
                val json = gson.toJson(conv)
                file.writeText(json)
                count++
            }
            return ExportResult(true, "Exported $count threads to internal storage", count)
        }
        return ExportResult(false, "Failed to access storage", 0)
    }
}
