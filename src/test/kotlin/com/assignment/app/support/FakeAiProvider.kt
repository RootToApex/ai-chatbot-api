package com.assignment.app.support

import com.assignment.app.ai.domain.AiChatRequest
import com.assignment.app.ai.domain.AiChatResult
import com.assignment.app.ai.domain.AiProvider
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * 테스트는 외부 키 없이 재현 가능해야 한다.
 * 마지막으로 받은 요청을 기록해 "무엇을 provider에 보냈는지"를 검증할 수 있게 한다.
 */
@Component
@Primary
class FakeAiProvider : AiProvider {

    @Volatile
    var lastRequest: AiChatRequest? = null

    override val name: String = "fake"

    override fun generate(request: AiChatRequest): AiChatResult {
        lastRequest = request
        return AiChatResult(content = answerFor(request), model = request.model ?: FAKE_MODEL)
    }

    override fun stream(request: AiChatRequest, onToken: (String) -> Unit): AiChatResult {
        lastRequest = request
        val answer = answerFor(request)
        answer.chunked(CHUNK_SIZE).forEach(onToken)
        return AiChatResult(content = answer, model = request.model ?: FAKE_MODEL)
    }

    /** 입력에 의해서만 결정되는 응답 — 같은 질문이면 항상 같은 답이다. */
    private fun answerFor(request: AiChatRequest): String {
        val lastQuestion = request.messages.lastOrNull()?.content.orEmpty()
        return "[fake] $lastQuestion (turns=${request.messages.size})"
    }

    companion object {
        const val FAKE_MODEL = "fake-model"
        private const val CHUNK_SIZE = 8
    }
}
