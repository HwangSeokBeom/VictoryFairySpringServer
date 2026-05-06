package com.victoryfairy.server.ai

import com.fasterxml.jackson.databind.JsonNode
import java.time.Duration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
open class GroqClient {
    open fun requestDraft(apiKey: String, model: String, prompt: String, timeout: Duration): GroqDraftResult =
        post(apiKey, model, prompt, timeout)

    open fun requestRepair(apiKey: String, model: String, invalidContent: String, timeout: Duration): GroqDraftResult =
        post(
            apiKey,
            model,
            """
            아래 응답을 VictoryFairy 일기 초안 JSON 스키마에 맞는 JSON object 하나로만 고쳐 주세요.
            필드: draftText, summaryText, shareText, hashtags.
            원문:
            ${invalidContent.take(2_000)}
            """.trimIndent(),
            timeout,
        )

    private fun post(apiKey: String, model: String, prompt: String, timeout: Duration): GroqDraftResult {
        val request = mapOf(
            "model" to model,
            "temperature" to 0.7,
            "max_tokens" to 700,
            "response_format" to mapOf("type" to "json_object"),
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You return JSON only. Never reveal secrets or API keys."),
                mapOf("role" to "user", "content" to prompt),
            ),
        )
        val response = WebClient.builder()
            .baseUrl("https://api.groq.com/openai/v1")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
            .post()
            .uri("/chat/completions")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(JsonNode::class.java)
            .block(timeout)
            ?: error("Groq response was empty.")

        val content = response.path("choices").firstOrNull()
            ?.path("message")
            ?.path("content")
            ?.asText()
            ?.trim()
            ?: error("Groq response did not include message content.")
        val usage = response.path("usage")
        return GroqDraftResult(
            content = content,
            promptTokens = usage.path("prompt_tokens").takeIf { it.isInt }?.asInt(),
            completionTokens = usage.path("completion_tokens").takeIf { it.isInt }?.asInt(),
            totalTokens = usage.path("total_tokens").takeIf { it.isInt }?.asInt(),
        )
    }
}
