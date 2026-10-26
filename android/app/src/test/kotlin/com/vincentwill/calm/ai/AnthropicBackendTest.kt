package com.vincentwill.calm.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.vincentwill.calm.classify.DecisionSource
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The real SDK against a local mock of the Messages API: request shape and fail-open on HTTP problems. */
class AnthropicBackendTest {
    private lateinit var server: MockWebServer

    private val request = AiRequest(
        appLabel = "WhatsApp",
        conversationTitle = "Familie",
        isGroup = true,
        messages = listOf("Mama" to "Wer holt Oma um 18 Uhr ab?"),
        now = ZonedDateTime.of(2026, 10, 26, 16, 0, 0, 0, ZoneId.of("Europe/Berlin")),
    )

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun backend(timeout: Duration = Duration.ofSeconds(5)) =
        AnthropicBackend("sk-ant-test", "claude-haiku-5-5", baseUrl = server.url("/").toString().trimEnd('/'), timeout = timeout)

    private fun messageResponse(text: String, stopReason: String = "end_turn") = MockResponse()
        .setHeader("content-type", "application/json")
        .setBody(
            ObjectMapper().writeValueAsString(
                mapOf(
                    "id" to "msg_test",
                    "type" to "message",
                    "role" to "assistant",
                    "model" to "claude-haiku-5-5",
                    "content" to listOf(mapOf("type" to "text", "text" to text)),
                    "stop_reason" to stopReason,
                    "stop_sequence" to null,
                    "usage" to mapOf("input_tokens" to 120, "output_tokens" to 20),
                ),
            ),
        )

    @Test
    fun `sends a structured output request and parses the answer`() = runBlocking {
        server.enqueue(messageResponse("""{"urgent": true, "reason": "Pickup today at 18:00", "confidence": 0.88}"""))

        val d = AiClassifier({ backend() }).classify(request)
        assertTrue(d.urgent)
        assertEquals(DecisionSource.AI, d.source)
        assertEquals("Pickup today at 18:00", d.reason)

        val recorded = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/v1/messages", recorded.path)
        assertEquals("sk-ant-test", recorded.getHeader("x-api-key"))
        val body = ObjectMapper().readTree(recorded.body.readUtf8())
        assertEquals("claude-haiku-5-5", body["model"].asText())
        assertEquals("json_schema", body["output_config"]["format"]["type"].asText())
        assertEquals("low", body["output_config"]["effort"].asText())
        val schema = body["output_config"]["format"]["schema"]
        assertEquals(listOf("urgent", "reason", "confidence"), schema["required"].map { it.asText() })
        assertFalse(schema["additionalProperties"].asBoolean())
        // Sampling parameters are rejected by the API since 2026-10-21.
        assertNull(body["temperature"])
        val user = body["messages"][0]["content"].let { if (it.isTextual) it.asText() else it[0]["text"].asText() }
        assertTrue(user, user.contains("Mama: Wer holt Oma um 18 Uhr ab?"))
        assertTrue(user, user.contains("Familie (group chat)"))
    }

    @Test
    fun `refusal stop reason fails open`() = runBlocking {
        server.enqueue(messageResponse("", stopReason = "refusal"))
        val d = AiClassifier({ backend() }).classify(request)
        assertTrue(d.urgent)
        assertTrue(d.reason, d.reason.startsWith("AI declined"))
    }

    @Test
    fun `http errors fail open without retrying`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(529).setBody("""{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""))
        val d = AiClassifier({ backend() }).classify(request)
        assertTrue(d.urgent)
        assertEquals(DecisionSource.FALLBACK, d.source)
        assertTrue(d.reason, d.reason.startsWith("AI error (HTTP 529)"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `invalid api key fails open`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))
        val d = AiClassifier({ backend() }).classify(request)
        assertTrue(d.urgent)
        assertTrue(d.reason, d.reason.startsWith("AI error (HTTP 401)"))
    }

    @Test
    fun `slow server times out and fails open`() = runBlocking {
        server.enqueue(messageResponse("""{"urgent": false, "reason": "x", "confidence": 1}""").setHeadersDelay(3, TimeUnit.SECONDS))
        val started = System.nanoTime()
        val d = AiClassifier({ backend() }, timeoutMs = 500).classify(request)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(d.urgent)
        assertTrue(d.reason, d.reason.startsWith("AI timed out"))
        assertTrue("took $elapsedMs ms", elapsedMs < 2_000)
    }
}
