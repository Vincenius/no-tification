package com.vincentwill.calm.listener

import android.app.Notification

enum class IgnoreReason { OWN_APP, ONGOING, FOREGROUND_SERVICE, GROUP_SUMMARY }

/** Decides which posted notifications never enter the pipeline. */
object InterceptFilter {
    fun ignoreReason(
        packageName: String,
        ownPackageName: String,
        flags: Int,
        isOngoing: Boolean,
    ): IgnoreReason? = when {
        packageName == ownPackageName -> IgnoreReason.OWN_APP
        flags and Notification.FLAG_FOREGROUND_SERVICE != 0 -> IgnoreReason.FOREGROUND_SERVICE
        isOngoing || flags and Notification.FLAG_ONGOING_EVENT != 0 -> IgnoreReason.ONGOING
        flags and Notification.FLAG_GROUP_SUMMARY != 0 -> IgnoreReason.GROUP_SUMMARY
        else -> null
    }
}
