package com.vincentwill.calm.ai

import com.vincentwill.calm.classify.DecisionSource
import com.vincentwill.calm.classify.DecisionStep
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fail-open behaviour, with the API replaced by fakes. Delays run on virtual time. */
class AiClassifierTest {
    private val request = AiRequest(
        appLabel = "WhatsApp",
        conversationTitle = "Anna",
        isGroup = false,
        messages = listOf("Anna" to "Bist du um 6 da?"),
        now = ZonedDateTime.of(2026, 10, 26, 17, 30, 0, 0, ZoneId.of("Europe/Berlin")),
    )

    private fun answering(text: String, stopReason: String? = "end_turn") =
        AiClassifier({ AiBackend { _, _ -> AiCompletion(text, stopReason) } })

    private fun assertFailOpen(classifier: AiClassifier, reasonStart: String) = runTest {
        val d = classifier.classify(request)
        assertTrue(d.urgent)
        assertEquals(DecisionSource.FALLBACK, d.source)
        assertTrue(d.reason, d.reason.startsWith(reasonStart))
    }

    @Test
    fun `confident urgent answer`() = runTest {
        val d = answering("""{"urgent": true, "reason": "Time-sensitive plan", "confidence": 0.92}""").classify(request)
        assertTrue(d.urgent)
        assertEquals(DecisionStep.AI, d.step)
        assertEquals("Time-sensitive plan", d.reason)
    }

    @Test
    fun `confident non-urgent answer holds`() = runTest {
        val d = answering("""{"urgent": false, "reason": "Group chatter", "confidence": 0.8}""").classify(request)
        assertFalse(d.urgent)
        assertEquals(DecisionSource.AI, d.source)
    }

    @Test
    fun `confidence exactly at the threshold is trusted`() = runTest {
        assertFalse(answering("""{"urgent": false, "reason": "x", "confidence": 0.6}""").classify(request).urgent)
    }

    @Test
    fun `low confidence fails open`() =
        assertFailOpen(answering("""{"urgent": false, "reason": "Probably chatter", "confidence": 0.45}"""), "AI unsure (45%): Probably chatter")

    @Test
    fun `invalid json fails open`() = assertFailOpen(answering("I think it can wait."), "AI answer unreadable")

    @Test
    fun `refusal fails open`() = assertFailOpen(answering("", stopReason = "refusal"), "AI declined")

    @Test
    fun `exceptions fail open`() =
        assertFailOpen(AiClassifier({ AiBackend { _, _ -> throw IOException("no network") } }), "AI error (network)")

    @Test
    fun `timeout after 5 seconds fails open`() = runTest {
        val slow = AiClassifier({ AiBackend { _, _ -> delay(10_000); AiCompletion("""{"urgent":false,"reason":"x","confidence":1}""", null) } })
        val started = testScheduler.currentTime
        val d = slow.classify(request)
        assertTrue(d.urgent)
        assertTrue(d.reason, d.reason.startsWith("AI timed out"))
        assertEquals(5_000, testScheduler.currentTime - started)
    }

    @Test
    fun `a fast answer within the timeout is used`() = runTest {
        val ok = AiClassifier({ AiBackend { _, _ -> delay(4_000); AiCompletion("""{"urgent":false,"reason":"x","confidence":0.9}""", null) } })
        assertFalse(ok.classify(request).urgent)
    }

    @Test
    fun `no backend means rules only and fail open`() = runTest {
        val none = AiClassifier({ null })
        assertFalse(none.isAvailable)
        val d = none.classify(request)
        assertTrue(d.urgent)
        assertEquals(DecisionSource.FALLBACK, d.source)
    }

    @Test
    fun `the backend receives the cleaned prompt`() = runTest {
        var sentUser = ""
        val classifier = AiClassifier({
            AiBackend { _, user ->
                sentUser = user
                AiCompletion("""{"urgent":true,"reason":"x","confidence":0.9}""", null)
            }
        })
        classifier.classify(request.copy(conversationTitle = "+49 151 23456789", messages = listOf("+49 151 23456789" to "Ruf mich an: 0171 9876543")))
        assertFalse(sentUser, sentUser.contains("151"))
        assertFalse(sentUser, sentUser.contains("9876543"))
        assertTrue(sentUser, sentUser.contains("unknown contact"))
        assertTrue(sentUser, sentUser.contains("Monday 2026-10-26 17:30"))
    }
}
