package com.vincentwill.calm.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseParserTest {
    @Test
    fun `parses a valid answer`() {
        val v = AiResponseParser.parse("""{"urgent": true, "reason": "Asks if you are coming at 6", "confidence": 0.9}""")!!
        assertTrue(v.urgent)
        assertEquals("Asks if you are coming at 6", v.reason)
        assertEquals(0.9, v.confidence, 1e-9)
    }

    @Test
    fun `tolerates code fences and surrounding text`() {
        val v = AiResponseParser.parse("Here you go:\n```json\n{\"urgent\": false, \"reason\": \"Newsletter\", \"confidence\": 0.95}\n```")!!
        assertFalse(v.urgent)
        assertEquals("Newsletter", v.reason)
    }

    @Test
    fun `integer confidence bounds are accepted`() {
        assertEquals(1.0, AiResponseParser.parse("""{"urgent": false, "reason": "x", "confidence": 1}""")!!.confidence, 0.0)
        assertEquals(0.0, AiResponseParser.parse("""{"urgent": true, "reason": "x", "confidence": 0}""")!!.confidence, 0.0)
    }

    @Test
    fun `missing or blank reason gets a default`() {
        assertEquals("AI: can wait", AiResponseParser.parse("""{"urgent": false, "confidence": 0.8}""")!!.reason)
        assertEquals("AI: needs attention", AiResponseParser.parse("""{"urgent": true, "reason": "  ", "confidence": 0.8}""")!!.reason)
    }

    @Test
    fun `invalid answers are rejected`() {
        listOf(
            "",
            "not json at all",
            "{",
            """{"urgent": "yes", "reason": "x", "confidence": 0.9}""",
            """{"urgent": 1, "reason": "x", "confidence": 0.9}""",
            """{"reason": "x", "confidence": 0.9}""",
            """{"urgent": true, "reason": "x"}""",
            """{"urgent": true, "reason": "x", "confidence": "high"}""",
            """{"urgent": true, "reason": "x", "confidence": 1.5}""",
            """{"urgent": true, "reason": "x", "confidence": -0.1}""",
            """{"urgent": true, "reason": "x", "confidence": 0.9""",
        ).forEach { assertNull(it, AiResponseParser.parse(it)) }
    }
}
