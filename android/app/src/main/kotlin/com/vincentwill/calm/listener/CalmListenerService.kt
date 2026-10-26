package com.vincentwill.calm.listener

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.vincentwill.calm.CalmGraph
import com.vincentwill.calm.pipeline.NotificationActions
import com.vincentwill.calm.util.SafeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Entry point of the pipeline. Bound by the system whenever notification access is granted, so it
 * runs without the Flutter engine and comes back after reboots and swipe-away.
 */
class CalmListenerService : NotificationListenerService(), NotificationActions {
    private lateinit var graph: CalmGraph
    private lateinit var extractor: NotificationExtractor
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Processes notifications strictly in arrival order. */
    private val queue = Channel<CapturedNotification>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        graph = CalmGraph.get(this)
        extractor = NotificationExtractor(applicationContext)
        scope.launch {
            for (n in queue) {
                try {
                    graph.pipeline.process(n, this@CalmListenerService)
                    graph.cleanupIfDue()
                } catch (e: Exception) {
                    SafeLog.w({ "pipeline failed for ${n.packageName}" }, e)
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        SafeLog.d { "listener connected" }
        graph.events.setListenerConnected(true)
        // Restores silencing if a disconnect had paused it.
        graph.health.check()
    }

    override fun onListenerDisconnected() {
        SafeLog.d { "listener disconnected, requesting rebind" }
        graph.events.setListenerConnected(false)
        graph.health.onListenerDisconnected()
        requestRebind(ComponentName(this, CalmListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val ignore = InterceptFilter.ignoreReason(
            packageName = sbn.packageName,
            ownPackageName = packageName,
            flags = sbn.notification.flags,
            isOngoing = sbn.isOngoing,
        )
        if (ignore != null) return

        val captured = try {
            extractor.extract(sbn)
        } catch (e: Exception) {
            // Malformed extras from some apps must never crash the listener.
            SafeLog.w({ "extract failed for ${sbn.packageName}" }, e)
            return
        }
        sbn.notification.contentIntent?.let { ContentIntentStore.put(sbn.key, it) }
        queue.trySend(captured)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        // The app withdrew the original (chat read elsewhere): withdraw our urgent copy as well.
        if (reason == REASON_APP_CANCEL || reason == REASON_APP_CANCEL_ALL) graph.reposter.cancelMirror(sbn.key)
    }

    // NotificationActions -------------------------------------------------------------------------

    override fun cancel(key: String) {
        try {
            cancelNotification(key)
        } catch (e: SecurityException) {
            // Thrown when the listener is not connected at the moment.
            SafeLog.w({ "cancel failed" }, e)
        }
    }

    override fun repostUrgent(source: CapturedNotification, items: List<IncomingItem>) =
        graph.reposter.post(source, items)
}
