package com.vincentwill.calm.digest

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vincentwill.calm.MainActivity
import com.vincentwill.calm.R
import com.vincentwill.calm.data.NotificationDao
import com.vincentwill.calm.silence.Channels
import com.vincentwill.calm.silence.canPostNotifications

/** Posts one summary notification for everything held. Held notifications are never re-posted one by one. */
class DigestDelivery(
    private val context: Context,
    private val dao: NotificationDao,
    private val channels: Channels,
) {
    /** Returns the number of newly released items (0 = nothing new, no notification posted). */
    suspend fun deliver(): Int {
        val fresh = dao.undeliveredHeld()
        if (fresh.isEmpty()) return 0
        val all = dao.unseenHeld()
        val apps = all.map { it.packageName }.distinct().size
        post(count = all.size, apps = apps, appLabels = all.map { it.appLabel }.distinct())
        dao.markDelivered(fresh.map { it.id })
        return fresh.size
    }

    private fun post(count: Int, apps: Int, appLabels: List<String>) {
        if (!canPostNotifications(context)) return
        channels.ensure()
        val title = "$count held notification${if (count == 1) "" else "s"} from $apps app${if (apps == 1) "" else "s"}"
        val open = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_DIGEST)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channels.digestId)
            .setSmallIcon(R.drawable.ic_stat_calm)
            .setContentTitle(title)
            .setContentText(appLabels.take(5).joinToString(", "))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        @Suppress("MissingPermission") // checked above
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun dismissSummary() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private companion object {
        const val NOTIFICATION_ID = 1
        const val REQUEST_CODE = 1
    }
}
