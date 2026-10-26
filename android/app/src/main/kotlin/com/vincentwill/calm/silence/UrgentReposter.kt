package com.vincentwill.calm.silence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vincentwill.calm.R
import com.vincentwill.calm.listener.CapturedNotification
import com.vincentwill.calm.listener.ContentIntentStore
import com.vincentwill.calm.listener.IncomingItem

/**
 * Re-posts urgent messages through Calm's DND-bypassing channel. One Calm notification per source
 * notification key; newer urgent messages update it (and alert again).
 *
 * The original stays in the shade (it keeps reply buttons and grouping), so our copy is mainly the
 * alert. It times out after [MIRROR_TIMEOUT_MS] to avoid permanent duplicates.
 */
class UrgentReposter(private val context: Context, private val channels: Channels) {
    private val nm = NotificationManagerCompat.from(context)

    fun post(source: CapturedNotification, items: List<IncomingItem>) {
        if (items.isEmpty() || !canPost()) return
        channels.ensure()

        val conversation = source.conversationTitle
        val title = when {
            source.isGroupConversation && conversation != null -> conversation
            else -> items.last().sender ?: source.title ?: source.appLabel
        }
        val lines = items.map { item ->
            if (source.isGroupConversation && item.sender != null) "${item.sender}: ${item.text}" else item.text
        }

        val builder = NotificationCompat.Builder(context, channels.urgentId)
            .setSmallIcon(R.drawable.ic_stat_calm)
            .setContentTitle(title)
            .setContentText(lines.last())
            .setSubText(source.appLabel)
            .setWhen(items.last().timestamp.takeIf { it > 0 } ?: source.postTime)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(source.category ?: NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setTimeoutAfter(MIRROR_TIMEOUT_MS)
            .setContentIntent(contentIntent(source.key, source.packageName))
        if (lines.size > 1) {
            builder.setStyle(NotificationCompat.InboxStyle().also { style -> lines.takeLast(6).forEach(style::addLine) })
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(lines.single()))
        }

        @Suppress("MissingPermission") // checked in canPost()
        nm.notify(TAG, source.key.hashCode(), builder.build())
    }

    /** Removes our mirror when the source app withdraws the original (e.g. the chat was read elsewhere). */
    fun cancelMirror(key: String) = nm.cancel(TAG, key.hashCode())

    private fun canPost(): Boolean = canPostNotifications(context)

    /** The original contentIntent, or launching the source app if we lost it (process death). */
    private fun contentIntent(key: String, packageName: String): PendingIntent? =
        ContentIntentStore.get(key) ?: context.packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(context, key.hashCode(), it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    private companion object {
        const val TAG = "urgent"
        const val MIRROR_TIMEOUT_MS = 60_000L
    }
}

/** POST_NOTIFICATIONS is a runtime permission only since Android 13. */
fun canPostNotifications(context: Context): Boolean {
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}
