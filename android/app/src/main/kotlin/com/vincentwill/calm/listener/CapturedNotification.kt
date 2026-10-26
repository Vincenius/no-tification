package com.vincentwill.calm.listener

/** One message from a MessagingStyle notification. */
data class CapturedMessage(
    val sender: String?,
    val text: String,
    val timestamp: Long,
    /** Sent by the user themselves (MessagingStyle messages without a sender person). */
    val fromSelf: Boolean = false,
)

/** Everything we extract from a posted notification. Free of Android types so it is unit-testable. */
data class CapturedNotification(
    val id: Long,
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val conversationTitle: String?,
    val isGroupConversation: Boolean,
    val category: String?,
    val postTime: Long,
    val messages: List<CapturedMessage>,
    val hasContentIntent: Boolean,
)
