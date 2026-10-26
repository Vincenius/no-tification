package com.vincentwill.calm.listener

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InterceptFilterTest {
    private val own = "com.vincentwill.calm"

    private fun reason(pkg: String = "com.whatsapp", flags: Int = 0, ongoing: Boolean = false) =
        InterceptFilter.ignoreReason(pkg, own, flags, ongoing)

    @Test
    fun `regular notification passes`() = assertNull(reason())

    @Test
    fun `own notifications are ignored`() = assertEquals(IgnoreReason.OWN_APP, reason(pkg = own))

    @Test
    fun `foreground service notifications are ignored`() =
        assertEquals(IgnoreReason.FOREGROUND_SERVICE, reason(flags = Notification.FLAG_FOREGROUND_SERVICE))

    @Test
    fun `ongoing notifications are ignored`() {
        assertEquals(IgnoreReason.ONGOING, reason(ongoing = true))
        assertEquals(IgnoreReason.ONGOING, reason(flags = Notification.FLAG_ONGOING_EVENT))
    }

    @Test
    fun `group summaries are ignored`() =
        assertEquals(IgnoreReason.GROUP_SUMMARY, reason(flags = Notification.FLAG_GROUP_SUMMARY))

    @Test
    fun `unrelated flags do not cause ignoring`() =
        assertNull(reason(flags = Notification.FLAG_AUTO_CANCEL or Notification.FLAG_ONLY_ALERT_ONCE))
}
