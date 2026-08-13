package com.assignment.app.ai.domain

/**
 * AI 응답 생성 포트. 도메인·유스케이스는 이 인터페이스만 알고, 특정 provider 규격은 어댑터가 흡수한다.
 *
 * 확장 지점:
 * - provider 교체: 이 인터페이스의 새 구현체를 infrastructure에 추가하면 된다 (도메인·서비스 무변경)
 * - RAG: 검색된 문서를 [AiChatRequest.context]에 실어 보내면 프롬프트 구성만 어댑터에서 바뀐다.
 *   문서 적재·임베딩·검색은 별도 컨텍스트로 두고, 이 포트는 "생성"만 책임진다
 */
interface AiProvider {
    /** 로그·응답에 노출할 provider 식별자 */
    val name: String

    fun generate(request: AiChatRequest): AiChatResult

    /**
     * 토큰이 생성될 때마다 [onToken]을 호출하고, 완료 후 전체 결과를 반환한다.
     * 스트리밍을 지원하지 않는 provider는 generate 결과를 한 번에 흘려보내면 된다.
     */
    fun stream(request: AiChatRequest, onToken: (String) -> Unit): AiChatResult
}

enum class AiRole { SYSTEM, USER, ASSISTANT }

data class AiMessage(val role: AiRole, val content: String)

data class AiChatRequest(
    val messages: List<AiMessage>,
    val model: String? = null,
    /** RAG 확장 지점 — 검색된 참고 문서. 현재는 비어 있다. */
    val context: List<String> = emptyList(),
)

data class AiChatResult(val content: String, val model: String)

/** provider 호출이 불가능하거나 실패했을 때. 호출부가 503으로 매핑한다. */
class AiProviderException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
