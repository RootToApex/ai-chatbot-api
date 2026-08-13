package com.assignment.app.ai.infrastructure

import com.assignment.app.ai.domain.AiChatRequest
import com.assignment.app.ai.domain.AiChatResult
import com.assignment.app.ai.domain.AiProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 키 없이 흐름을 시연하기 위한 결정론적 provider. `AI_PROVIDER=fake`로 **명시할 때만** 활성화된다.
 *
 * 기본값은 실제 호출이며, 키가 없으면 조용히 흉내내지 않고 503으로 실패한다 —
 * 가짜 응답이 기본 경로가 되면 "API로 AI를 활용한다"는 시연 목표가 가려지기 때문이다.
 */
@Component
@ConditionalOnProperty(name = ["ai.provider"], havingValue = "fake")
class FakeAiProvider : AiProvider {

    override val name: String = "fake"

    override fun generate(request: AiChatRequest): AiChatResult =
        AiChatResult(content = answerFor(request), model = request.model ?: FAKE_MODEL)

    override fun stream(request: AiChatRequest, onToken: (String) -> Unit): AiChatResult {
        val answer = answerFor(request)
        answer.chunked(CHUNK_SIZE).forEach(onToken)
        return AiChatResult(content = answer, model = request.model ?: FAKE_MODEL)
    }

    /** 입력에 의해서만 결정되는 응답 — 같은 질문이면 항상 같은 답이다. */
    private fun answerFor(request: AiChatRequest): String {
        val question = request.messages.lastOrNull()?.content.orEmpty()
        val turns = request.messages.size
        return "[fake] \"$question\"에 대한 모의 응답입니다. (이 스레드에서 함께 보낸 메시지 수: $turns)"
    }

    companion object {
        private const val FAKE_MODEL = "fake-model"
        private const val CHUNK_SIZE = 12
    }
}
