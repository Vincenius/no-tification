package com.vincentwill.calm.classify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpDetectorTest {
    @Test
    fun `english codes`() {
        listOf(
            "Your verification code is 482913",
            "G-482913 is your Google verification code.",
            "Use 4829 to sign in to your account",
            "Your OTP: 123 456",
            "2FA code 1234-5678",
            "Your one-time passcode is 90817",
        ).forEach { assertTrue(it, OtpDetector.containsOtp(it)) }
    }

    @Test
    fun `german codes`() {
        listOf(
            "Ihr Bestätigungscode lautet 4821",
            "482913 ist dein Instagram-Code",
            "Dein Anmeldecode: 551 902",
            "Ihre mTAN für die Überweisung: 734512",
            "Verifizierungscode 99812 – nicht weitergeben",
            "Ihre PIN lautet 4410",
        ).forEach { assertTrue(it, OtpDetector.containsOtp(it)) }
    }

    @Test
    fun `numbers without keywords are not codes`() {
        listOf(
            "Meeting moved to 1500",
            "Paket 48291 wurde zugestellt",
            "See you at 1830!",
        ).forEach { assertFalse(it, OtpDetector.containsOtp(it)) }
    }

    @Test
    fun `keywords without a code are not codes`() {
        listOf(
            "Your code review is ready",
            "Bestätigung deiner Bestellung",
            "Going shopping, need anything?",
            "Rabattcode SOMMER25 gilt bis Sonntag",
        ).forEach { assertFalse(it, OtpDetector.containsOtp(it)) }
    }

    @Test
    fun `long numbers and prices are not codes`() {
        listOf(
            "Login from IP, ref 123456789012",
            "Code für 1234,50 € eingelöst",
            "Call me back at +49 151 23456789 code",
        ).forEach { assertFalse(it, OtpDetector.containsOtp(it)) }
    }

    @Test
    fun `short keyword needs word boundaries`() {
        assertFalse(OtpDetector.containsOtp("Shopping list for 2026: milk"))
    }
}
