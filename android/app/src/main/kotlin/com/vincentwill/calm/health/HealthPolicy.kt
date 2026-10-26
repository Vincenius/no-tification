package com.vincentwill.calm.health

enum class HealthAction {
    /** All good (or Calm is off). */
    NOTHING,

    /** Ask Android to bind the listener again, and check again soon. */
    REBIND,

    /**
     * The listener has been gone too long. While Calm's DND mode is active nothing would come
     * through at all, so fail open: pause silencing and warn.
     */
    FAILSAFE,

    /** The listener is back after a failsafe: silence again. */
    RESTORE,
}

object HealthPolicy {
    /** Android often rebinds within seconds; give it this long before pausing silencing. */
    const val GRACE_MS = 2 * 60_000L

    fun decide(
        enabled: Boolean,
        hasAccess: Boolean,
        connected: Boolean,
        suspended: Boolean,
        disconnectedSince: Long?,
        now: Long,
    ): HealthAction = when {
        // Turning Calm off already turned DND off and cleared the suspended flag.
        !enabled -> HealthAction.NOTHING
        connected && hasAccess -> if (suspended) HealthAction.RESTORE else HealthAction.NOTHING
        // Already failed open: keep trying to get the listener back, without warning again.
        suspended -> HealthAction.REBIND
        // Access revoked: nothing can arrive any more.
        !hasAccess -> HealthAction.FAILSAFE
        disconnectedSince == null || now - disconnectedSince < GRACE_MS -> HealthAction.REBIND
        else -> HealthAction.FAILSAFE
    }
}
