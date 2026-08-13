package com.assignment.app.chat.application

import com.assignment.app.ai.domain.AiChatRequest
import com.assignment.app.ai.domain.AiChatResult
import com.assignment.app.ai.domain.AiMessage
import com.assignment.app.ai.domain.AiProvider
import com.assignment.app.ai.domain.AiProviderException
import com.assignment.app.ai.domain.AiRole
import com.assignment.app.common.ApiException
import com.assignment.app.common.PageParams
import com.assignment.app.common.PageResponse
import com.assignment.app.common.SortDirection
import com.assignment.app.config.AuthenticatedUser
import com.assignment.app.chat.domain.Chat
import com.assignment.app.chat.domain.ChatRepository
import com.assignment.app.chat.domain.ChatThreadRepository
import com.assignment.app.user.domain.Role
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ChatService(
    private val threadResolver: ChatThreadResolver,
    private val chatWriter: ChatWriter,
    private val chatRepository: ChatRepository,
    private val threadRepository: ChatThreadRepository,
    private val aiProvider: AiProvider,
    @Value("\${chat.history-size:10}") private val historySize: Int,
) {

    /**
     * 대화 생성. AI 호출은 어떤 트랜잭션 안에도 두지 않는다 —
     * 수십 초짜리 외부 호출이 커넥션을 잡고 있으면 동시 요청 몇 개로 풀이 마른다.
     * 순서: 이력 조회(짧은 tx) → AI 호출(tx 밖) → 스레드 확정·대화 저장(짧은 tx)
     */
    fun create(userId: Long, request: ChatCreateRequest): ChatResponse {
        val question = requireQuestion(request)
        val now = Instant.now()
        val history = loadHistory(userId, now)
        val result = callAi(history, question, request.model)
        return ChatResponse.from(persist(userId, question, result))
    }

    /**
     * 스트리밍 생성. 토큰은 [onToken]으로 흘리고, 스트림이 끝난 뒤에야 저장한다.
     * 중간에 실패하면 저장하지 않는다(비스트리밍과 동일한 정책).
     */
    fun createStreaming(userId: Long, request: ChatCreateRequest, onToken: (String) -> Unit): ChatResponse {
        val question = requireQuestion(request)
        val now = Instant.now()
        val history = loadHistory(userId, now)
        val result = try {
            aiProvider.stream(AiChatRequest(messages = history + AiMessage(AiRole.USER, question), model = request.model), onToken)
        } catch (e: AiProviderException) {
            throw unavailable(e)
        }
        return ChatResponse.from(persist(userId, question, result))
    }

    /** 대화 목록 — 스레드를 페이징하고, 각 스레드의 대화는 별도 조회로 채운다. */
    @Transactional(readOnly = true)
    fun list(user: AuthenticatedUser, page: Int, size: Int, sort: String): PageResponse<ThreadGroupResponse> {
        PageParams.validate(page, size)
        val direction = PageParams.direction(sort)
        val pageable = PageRequest.of(page, size)

        // 컬렉션 fetch join + Pageable 조합은 하이버네이트가 메모리에서 페이징하므로 2단계로 나눈다
        val threads = when {
            user.role == Role.ADMIN && direction == SortDirection.ASC -> threadRepository.findAllByOrderByCreatedAtAscIdAsc(pageable)
            user.role == Role.ADMIN -> threadRepository.findAllByOrderByCreatedAtDescIdDesc(pageable)
            direction == SortDirection.ASC -> threadRepository.findByUserIdOrderByCreatedAtAscIdAsc(user.id, pageable)
            else -> threadRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.id, pageable)
        }

        val threadIds = threads.content.mapNotNull { it.id }
        val chatsByThread = if (threadIds.isEmpty()) {
            emptyMap()
        } else {
            chatRepository.findByThreadIdInOrderByCreatedAtAscIdAsc(threadIds).groupBy { it.threadId }
        }

        return PageResponse.from(threads) { thread ->
            ThreadGroupResponse.of(thread, thread.id?.let { chatsByThread[it] }.orEmpty())
        }
    }

    /** 스레드 삭제 — 본인 것만. 하위 대화·피드백은 FK CASCADE로 함께 삭제된다. */
    @Transactional
    fun deleteThread(user: AuthenticatedUser, threadId: Long) {
        val thread = threadRepository.findById(threadId).orElseThrow {
            ApiException.notFound("THREAD_NOT_FOUND", "스레드를 찾을 수 없습니다")
        }
        if (thread.userId != user.id) {
            throw ApiException.forbidden("THREAD_FORBIDDEN", "자신이 생성한 스레드만 삭제할 수 있습니다")
        }
        threadRepository.delete(thread)
    }

    /**
     * LLM에 보낼 이력은 현재 스레드의 최근 N개로 자른다.
     * 전체 전송은 토큰·지연이 대화 길이에 비례해 늘다가 컨텍스트 한도에서 실패한다.
     * DB에는 전부 저장되며 조회 API는 전체를 돌려준다.
     */
    private fun loadHistory(userId: Long, now: Instant): List<AiMessage> {
        val thread = threadResolver.findActiveThread(userId, now) ?: return emptyList()
        val threadId = thread.id ?: return emptyList()
        val recent = chatRepository.findByThreadIdOrderByCreatedAtDescIdDesc(threadId, PageRequest.of(0, historySize))
        return recent.reversed().flatMap { toMessages(it) }
    }

    private fun toMessages(chat: Chat) = listOf(
        AiMessage(AiRole.USER, chat.question),
        AiMessage(AiRole.ASSISTANT, chat.answer),
    )

    private fun callAi(history: List<AiMessage>, question: String, model: String?): AiChatResult = try {
        aiProvider.generate(AiChatRequest(messages = history + AiMessage(AiRole.USER, question), model = model))
    } catch (e: AiProviderException) {
        throw unavailable(e)
    }

    private fun persist(userId: Long, question: String, result: AiChatResult): Chat {
        val committedAt = Instant.now()
        val thread = threadResolver.commitQuestion(userId, committedAt)
        val threadId = requireNotNull(thread.id) { "스레드 저장에 실패했습니다" }
        return chatWriter.save(threadId, question, result.content, result.model, committedAt)
    }

    private fun requireQuestion(request: ChatCreateRequest): String =
        request.question?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ApiException.badRequest("VALIDATION_FAILED", "질문은 필수입니다")

    private fun unavailable(e: AiProviderException) =
        ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", e.message ?: "AI 응답을 생성할 수 없습니다")
}
