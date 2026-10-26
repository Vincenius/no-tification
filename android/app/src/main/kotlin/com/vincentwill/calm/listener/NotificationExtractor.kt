package com.vincentwill.calm.listener

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Turns a [StatusBarNotification] into a [CapturedNotification]. */
class NotificationExtractor(private val context: Context) {
    private val labelCache = ConcurrentHashMap<String, String>()

    fun extract(sbn: StatusBarNotification): CapturedNotification {
        val notification = sbn.notification
        val extras = notification.extras
        val style = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification)

        val messages = style?.messages.orEmpty().mapNotNull { message ->
            val text = message.text?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CapturedMessage(
                sender = message.person?.name?.toString(),
                text = text,
                timestamp = message.timestamp,
                fromSelf = message.person == null,
            )
        }

        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }

        return CapturedNotification(
            id = nextId.incrementAndGet(),
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel(sbn.packageName),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = bigText ?: textLines ?: text,
            conversationTitle = style?.conversationTitle?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            isGroupConversation = style?.isGroupConversation
                ?: extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false),
            category = notification.category,
            postTime = sbn.postTime,
            messages = messages,
            hasContentIntent = notification.contentIntent != null,
        )
    }

    private fun appLabel(packageName: String): String = labelCache.getOrPut(packageName) {
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private companion object {
        val nextId = AtomicLong(0)
    }
}
