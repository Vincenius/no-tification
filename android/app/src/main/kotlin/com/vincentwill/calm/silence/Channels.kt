package com.vincentwill.calm.silence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.vincentwill.calm.data.Settings

/**
 * Calm's own channels. Both must bypass Do Not Disturb, since Calm's DND mode silences everything
 * else.
 *
 * Android only honours `setBypassDnd` when the channel is created by an app that already has DND
 * access, and apps can't change it later. So when access is granted after the channels exist, we
 * create a new generation of channels with new ids.
 */
class Channels(private val context: Context, private val settings: Settings) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    val urgentId: String get() = "urgent_v${settings.channelGeneration}"
    val digestId: String get() = "digest_v${settings.channelGeneration}"

    /** Health warnings. Only posted while Calm's DND mode is paused, so no bypass needed. */
    val statusId = "status"

    fun ensure() {
        val hasDndAccess = nm.isNotificationPolicyAccessGranted
        if (hasDndAccess && !settings.channelsCreatedWithDndAccess) {
            if (nm.getNotificationChannel(urgentId) != null) {
                nm.deleteNotificationChannel(urgentId)
                nm.deleteNotificationChannel(digestId)
                settings.channelGeneration += 1
            }
            settings.channelsCreatedWithDndAccess = true
        }
        // Recreating an existing channel only updates its name and description (e.g. after a rename).
        nm.createNotificationChannel(
            NotificationChannel(statusId, "no-tification status", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Warnings when no-tification can't do its job, e.g. the listener disconnected"
            },
        )
        if (nm.getNotificationChannel(urgentId) != null) return

        val urgent = NotificationChannel(urgentId, "Urgent", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notifications no-tification let through immediately"
            setBypassDnd(true)
            enableVibration(true)
        }
        val digest = NotificationChannel(digestId, "Digest", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Summary of held notifications at your digest times"
            setBypassDnd(true)
        }
        nm.createNotificationChannels(listOf(urgent, digest))
    }

    fun urgentBypassesDnd(): Boolean = nm.getNotificationChannel(urgentId)?.canBypassDnd() == true
}
