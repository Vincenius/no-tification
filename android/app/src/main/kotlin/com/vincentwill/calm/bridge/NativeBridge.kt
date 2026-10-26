package com.vincentwill.calm.bridge

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.vincentwill.calm.listener.CalmListenerService
import com.vincentwill.calm.listener.ContentIntentStore
import com.vincentwill.calm.listener.LiveLog
import com.vincentwill.calm.util.SafeLog
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Flutter ↔ native boundary. Flutter only renders; all state lives on the native side.
 *
 * Method channel: request/response calls. Event channel: a stream of maps tagged with "type".
 */
class NativeBridge(private val activity: Context, messenger: BinaryMessenger) :
    MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var eventJob: Job? = null

    init {
        MethodChannel(messenger, METHOD_CHANNEL).setMethodCallHandler(this)
        EventChannel(messenger, EVENT_CHANNEL).setStreamHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getStatus" -> result.success(status())
            "openNotificationAccessSettings" -> {
                openNotificationAccessSettings()
                result.success(null)
            }
            "getLog" -> result.success(LiveLog.snapshot().map { it.toMap() })
            "clearLog" -> {
                LiveLog.clear()
                result.success(null)
            }
            "openNotification" -> {
                val key = call.argument<String>("key")
                val packageName = call.argument<String>("packageName")
                result.success(openNotification(key, packageName))
            }
            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        eventJob?.cancel()
        eventJob = scope.launch {
            launch {
                LiveLog.events.collect { events.success(mapOf("type" to "notification", "data" to it.toMap())) }
            }
            launch {
                // The initial value is already available via getStatus.
                LiveLog.listenerConnected.drop(1).collect { events.success(mapOf("type" to "status", "data" to status())) }
            }
        }
    }

    override fun onCancel(arguments: Any?) {
        eventJob?.cancel()
        eventJob = null
    }

    fun dispose() = scope.cancel()

    private fun status(): Map<String, Any> = mapOf(
        "notificationAccess" to NotificationManagerCompat.getEnabledListenerPackages(activity)
            .contains(activity.packageName),
        "listenerConnected" to LiveLog.listenerConnected.value,
        "ignoredCount" to LiveLog.ignoredCount,
    )

    private fun openNotificationAccessSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(activity, CalmListenerService::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            // Some OEMs don't implement the detail screen.
            activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    /** Fires the original contentIntent; falls back to launching the app. Returns true on success. */
    private fun openNotification(key: String?, packageName: String?): Boolean {
        val pendingIntent = key?.let { ContentIntentStore.get(it) }
        if (pendingIntent != null) {
            try {
                pendingIntent.send(activity, 0, null, null, null, null, sendOptions())
                return true
            } catch (_: PendingIntent.CanceledException) {
                SafeLog.d { "contentIntent canceled, launching app instead" }
            }
        }
        val launch = packageName?.let { activity.packageManager.getLaunchIntentForPackage(it) }
            ?: return false
        activity.startActivity(launch)
        return true
    }

    /** Since Android 14 the sender has to opt in to lend its foreground state to the PendingIntent. */
    @Suppress("DEPRECATION")
    private fun sendOptions() = when {
        Build.VERSION.SDK_INT >= 36 -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE,
            ).toBundle()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            ).toBundle()
        else -> null
    }

    private companion object {
        const val METHOD_CHANNEL = "com.vincentwill.calm/native"
        const val EVENT_CHANNEL = "com.vincentwill.calm/events"
    }
}
