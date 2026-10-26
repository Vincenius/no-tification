package com.vincentwill.calm.ai

import com.anthropic.client.AnthropicClientAsync
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import java.time.Duration
import kotlinx.coroutines.future.await

/** Anthropic Messages API via the official SDK. */
class AnthropicBackend(
    apiKey: String,
    private val model: String,
    baseUrl: String? = null,
    timeout: Duration = Duration.ofSeconds(5),
) : AiBackend {
    private val client: AnthropicClientAsync = AnthropicOkHttpClientAsync.builder()
        .apiKey(apiKey)
        .apply { if (baseUrl != null) baseUrl(baseUrl) }
        .timeout(timeout)
        // A retry can't fit into the 5 s budget; we fail open instead.
        .maxRetries(0)
        .build()

    override suspend fun complete(system: String, user: String): AiCompletion {
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(1024L)
            .system(system)
            .addUserMessage(user)
            .outputConfig(
                OutputConfig.builder()
                    // A short classification: low effort keeps latency down.
                    .effort(OutputConfig.Effort.LOW)
                    .format(JsonOutputFormat.builder().schema(AiPrompt.schema).build())
                    .build(),
            )
            .build()
        val message = client.messages().create(params).await()
        return AiCompletion(
            text = message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString(""),
            stopReason = message.stopReason().map { it.asString() }.orElse(null),
        )
    }

    fun close() = client.close()
}
