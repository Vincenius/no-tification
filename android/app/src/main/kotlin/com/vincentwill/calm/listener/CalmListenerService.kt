package com.vincentwill.calm.listener

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.vincentwill.calm.util.SafeLog

/**
 * Entry point of the pipeline. Bound by the system whenever notification access is granted, so it
 * runs without the Flutter engine and comes back after reboots and swipe-away.
 *
 * Milestone 1: capture and log only, no silencing.
 */
class CalmListenerService : NotificationListenerService() {
    private lateinit var extractor: NotificationExtractor

    override fun onCreate() {
        super.onCreate()
        extractor = NotificationExtractor(applicationContext)
    }

    override fun onListenerConnected() {
        SafeLog.d { "listener connected" }
        LiveLog.setListenerConnected(true)
    }

    override fun onListenerDisconnected() {
        SafeLog.d { "listener disconnected, requesting rebind" }
        LiveLog.setListenerConnected(false)
        requestRebind(ComponentName(this, CalmListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val ignore = InterceptFilter.ignoreReason(
            packageName = sbn.packageName,
            ownPackageName = packageName,
            flags = sbn.notification.flags,
            isOngoing = sbn.isOngoing,
        )
        if (ignore != null) {
            LiveLog.countIgnored()
            return
        }

        val captured = try {
            extractor.extract(sbn)
        } catch (e: Exception) {
            // Malformed extras from some apps must never crash the listener.
            SafeLog.w({ "extract failed for ${sbn.packageName}" }, e)
            return
        }
        sbn.notification.contentIntent?.let { ContentIntentStore.put(sbn.key, it) }
        LiveLog.add(captured)
        SafeLog.d { "captured from ${sbn.packageName} (${captured.messages.size} messages)" }
    }
}
