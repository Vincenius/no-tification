package com.vincentwill.calm.health

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthPolicyTest {
    private val now = 1_000_000_000L

    private fun decide(
        enabled: Boolean = true,
        access: Boolean = true,
        connected: Boolean = true,
        suspended: Boolean = false,
        since: Long? = null,
    ) = HealthPolicy.decide(enabled, access, connected, suspended, since, now)

    @Test
    fun `healthy listener needs nothing`() = assertEquals(HealthAction.NOTHING, decide())

    @Test
    fun `calm off never acts`() {
        assertEquals(HealthAction.NOTHING, decide(enabled = false, connected = false, since = now - 3_600_000))
        assertEquals(HealthAction.NOTHING, decide(enabled = false, access = false))
    }

    @Test
    fun `fresh disconnect asks for a rebind first`() {
        assertEquals(HealthAction.REBIND, decide(connected = false, since = now))
        assertEquals(HealthAction.REBIND, decide(connected = false, since = now - HealthPolicy.GRACE_MS + 1))
    }

    @Test
    fun `disconnect beyond the grace period fails open`() =
        assertEquals(HealthAction.FAILSAFE, decide(connected = false, since = now - HealthPolicy.GRACE_MS))

    @Test
    fun `revoked access fails open immediately`() =
        assertEquals(HealthAction.FAILSAFE, decide(access = false, connected = true, since = now))

    @Test
    fun `while suspended keep rebinding without warning again`() =
        assertEquals(HealthAction.REBIND, decide(connected = false, suspended = true, since = now - 3_600_000))

    @Test
    fun `listener back after failsafe restores silencing`() =
        assertEquals(HealthAction.RESTORE, decide(connected = true, suspended = true))
}
