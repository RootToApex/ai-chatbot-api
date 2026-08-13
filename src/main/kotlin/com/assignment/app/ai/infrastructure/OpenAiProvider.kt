package com.assignment.app.ai.infrastructure

import com.assignment.app.ai.domain.AiChatRequest
import com.assignment.app.ai.domain.AiChatResult
import com.assignment.app.ai.domain.AiMessage
import com.assignment.app.ai.domain.AiProvider
import com.assignment.app.ai.domain.AiProviderException
import com.assignment.app.ai.domain.AiRole
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration

/**
 * OpenAI Chat Completions 어댑터.
 *
 * - 도메인 역할(USER/ASSISTANT)을 provider 규격 문자열로 여기서 변환한다.
 *   도메인 문자열을 외부 계약에 그대로 흘리면 provider를 바꿀 때 도메인이 따라 깨진다
 * - 재시도는 하지 않는다. 타임아웃 × 재시도는 요청 하나가 수 분을 삼킨다
 * - 키가 없으면 호출을 시도하지 않고 즉시 실패시킨다 (더미 키로 외부를 두드리지 않는다)
 */
@Component
class OpenAiProvider(
    @Value("\${ai.openai.api-key:}") private val apiKey: String,
    @Value("\${ai.openai.base-url:https://api.openai.com}") private val baseUrl: String,
    @Value("\${ai.openai.default-model:gpt-4o-mini}") private val defaultModel: String,
    @Value("\${ai.openai.timeout-seconds:60}") private val timeoutSeconds: Long,
    private val objectMapper: ObjectMapper,
) : AiProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = "openai"

    private val restClient: RestClient by lazy {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(10))
            setReadTimeout(Duration.ofSeconds(timeoutSeconds))
        }
        RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build()
    }

    override fun generate(request: AiChatRequest): AiChatResult {
        requireKey()
        val model = request.model ?: defaultModel
        val response = try {
            restClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload(request, model, stream = false))
                .retrieve()
                .body(String::class.java)
        } catch (e: RestClientException) {
            // 예외 메시지에 키가 섞이지 않도록 상태·유형만 남긴다
            log.error("AI 호출 실패: {}", e.javaClass.simpleName)
            throw AiProviderException("AI 응답 생성에 실패했습니다", e)
        } ?: throw AiProviderException("AI 응답이 비어 있습니다")

        val root = objectMapper.readTree(response)
        val content = root.path("choices").firstOrNull()?.path("message")?.path("content")?.asText()
            ?: throw AiProviderException("AI 응답 형식을 해석할 수 없습니다")
        return AiChatResult(content = content, model = root.path("model").asText(model))
    }

    override fun stream(request: AiChatRequest, onToken: (String) -> Unit): AiChatResult {
        requireKey()
        val model = request.model ?: defaultModel
        val buffer = StringBuilder()
        try {
            restClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload(request, model, stream = true))
                .exchange { _, response ->
                    response.body.bufferedReader().forEachLine { line ->
                        val token = parseStreamLine(line) ?: return@forEachLine
                        buffer.append(token)
                        onToken(token)
                    }
                }
        } catch (e: RestClientException) {
            log.error("AI 스트리밍 호출 실패: {}", e.javaClass.simpleName)
            throw AiProviderException("AI 응답 생성에 실패했습니다", e)
        }
        if (buffer.isEmpty()) throw AiProviderException("AI 응답이 비어 있습니다")
        return AiChatResult(content = buffer.toString(), model = model)
    }

    /** `data: {...}` 한 줄에서 델타 토큰만 뽑는다. 종료 신호(`[DONE]`)와 빈 줄은 무시. */
    private fun parseStreamLine(line: String): String? {
        if (!line.startsWith(DATA_PREFIX)) return null
        val payload = line.removePrefix(DATA_PREFIX).trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        val node: JsonNode = try {
            objectMapper.readTree(payload)
        } catch (e: Exception) {
            return null
        }
        val token = node.path("choices").firstOrNull()?.path("delta")?.path("content")?.asText().orEmpty()
        return token.ifEmpty { null }
    }

    private fun payload(request: AiChatRequest, model: String, stream: Boolean): Map<String, Any> {
        val messages = buildList {
            if (request.context.isNotEmpty()) {
                add(mapOf("role" to "system", "content" to request.context.joinToString("\n\n")))
            }
            addAll(request.messages.map { mapOf("role" to providerRole(it), "content" to it.content) })
        }
        return mapOf("model" to model, "messages" to messages, "stream" to stream)
    }

    private fun providerRole(message: AiMessage): String = when (message.role) {
        AiRole.SYSTEM -> "system"
        AiRole.USER -> "user"
        AiRole.ASSISTANT -> "assistant"
    }

    private fun requireKey() {
        if (apiKey.isBlank()) {
            throw AiProviderException("AI API 키가 설정되지 않았습니다 (OPENAI_API_KEY)")
        }
    }

    companion object {
        private const val DATA_PREFIX = "data:"
    }
}
