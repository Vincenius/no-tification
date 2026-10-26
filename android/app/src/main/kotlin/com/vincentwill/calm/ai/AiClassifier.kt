package com.vincentwill.calm.ai

import com.anthropic.errors.AnthropicServiceException
import com.fasterxml.jackson.databind.ObjectMapper
import com.vincentwill.calm.classify.Decision
import com.vincentwill.calm.classify.DecisionStep
import com.vincentwill.calm.classify.RulesEngine
import com.vincentwill.calm.util.SafeLog
import java.io.IOException
import java.util.concurrent.CompletionException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class AiCompletion(val text: String, val stopReason: String?)

/** The model call, abstracted so the fail-open logic can be tested without the network. */
fun interface AiBackend {
    suspend fun complete(system: String, user: String): AiCompletion
}

data class AiVerdict(val urgent: Boolean, val reason: String, val confidence: Double)

/** Strict parser for {"urgent": bool, "reason": string, "confidence": 0–1}. Anything else is null. */
object AiResponseParser {
    private val mapper = ObjectMapper()

    fun parse(text: String): AiVerdict? {
        // Structured outputs return bare JSON; tolerate code fences or stray text around it anyway.
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val node = try {
            mapper.readTree(text.substring(start, end + 1))
        } catch (_: IOException) {
            return null
        }
        val urgent = node.get("urgent")?.takeIf { it.isBoolean }?.booleanValue() ?: return null
        val confidence = node.get("confidence")?.takeIf { it.isNumber }?.doubleValue() ?: return null
        if (confidence.isNaN() || confidence < 0.0 || confidence > 1.0) return null
        val reason = node.get("reason")?.takeIf { it.isTextual }?.textValue()?.trim().orEmpty()
        return AiVerdict(urgent, reason.ifEmpty { if (urgent) "AI: needs attention" else "AI: can wait" }, confidence)
    }
}

/**
 * Gray-zone classification. Fails open: no backend, timeout, error, refusal, unreadable answer or
 * low confidence all mean urgent. Missing an urgent message is worse than an extra interruption.
 */
class AiClassifier(
    private val backend: () -> AiBackend?,
    private val timeoutMs: Long = 5_000,
    private val minConfidence: Double = 0.6,
) {
    val isAvailable: Boolean get() = backend() != null

    suspend fun classify(req: AiRequest): Decision {
        val ai = backend() ?: return RulesEngine.fallback("No AI configured, letting it through")
        val completion = try {
            withTimeout(timeoutMs) { ai.complete(AiPrompt.SYSTEM, AiPrompt.userContent(req)) }
        } catch (_: TimeoutCancellationException) {
            return failOpen("AI timed out")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.w({ "AI call failed" }, e)
            return failOpen("AI error (${describe(e)})")
        }
        if (completion.stopReason.equals("refusal", ignoreCase = true)) return failOpen("AI declined to answer")
        val verdict = AiResponseParser.parse(completion.text) ?: return failOpen("AI answer unreadable")
        if (verdict.confidence < minConfidence) {
            return failOpen("AI unsure (${(verdict.confidence * 100).toInt()}%): ${verdict.reason}")
        }
        return Decision(verdict.urgent, verdict.reason, DecisionStep.AI)
    }

    private fun failOpen(reason: String) = Decision(urgent = true, reason = "$reason, letting it through", step = DecisionStep.FALLBACK)

    private fun describe(e: Throwable): String = when (val cause = if (e is CompletionException) e.cause ?: e else e) {
        is AnthropicServiceException -> "HTTP ${cause.statusCode()}"
        is IOException -> "network"
        else -> cause.javaClass.simpleName
    }
}
