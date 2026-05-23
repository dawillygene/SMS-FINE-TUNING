package com.dawillygene.smsclone.data.model

data class SmsMessage(
    val id: Long,
    val address: String,
    val body: String?,
    val date: Long,
    val type: Int,    // 1 = received, 2 = sent
    val threadId: Long
)

data class Conversation(
    val threadId: Long,
    val contactName: String,
    val address: String,
    val messages: List<MessageEntry>
)

data class MessageEntry(
    val sender: String,  // "me" or "contactName"
    val body: String?,
    val timestamp: Long,
    val dateFormatted: String
)

data class ExportResult(val success: Boolean, val message: String, val count: Int)
