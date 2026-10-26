package com.vincentwill.calm.listener

/** One message from a MessagingStyle notification. */
data class CapturedMessage(
    val sender: String?,
    val text: String,
    val timestamp: Long,
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
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "key" to key,
        "packageName" to packageName,
        "appLabel" to appLabel,
        "title" to title,
        "text" to text,
        "conversationTitle" to conversationTitle,
        "isGroupConversation" to isGroupConversation,
        "category" to category,
        "postTime" to postTime,
        "messages" to messages.map {
            mapOf("sender" to it.sender, "text" to it.text, "timestamp" to it.timestamp)
        },
        "hasContentIntent" to hasContentIntent,
    )
}
