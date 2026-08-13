package com.assignment.app.domain.chat.service

import com.assignment.app.domain.chat.entity.Chat
import com.assignment.app.domain.chat.entity.ChatThread
import com.assignment.app.domain.chat.repository.ChatRepository
import com.assignment.app.domain.chat.repository.ChatThreadRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 대화 생성의 쓰기 경계. AI 호출은 이 트랜잭션 밖에서 이미 끝나 있어야 한다.
 *
 * 스레드 확정과 대화 저장을 **한 트랜잭션**에서 처리한다. 두 트랜잭션으로 나누면 그 사이에 잠금이 풀려,
 * 같은 사용자가 그 틈에 스레드를 삭제하면 대화 저장이 참조 무결성 위반으로 실패하고
 * 대화가 하나도 없는 스레드가 남는다.
 */
@Service
class ChatWriter(
    private val threadRepository: ChatThreadRepository,
    private val chatRepository: ChatRepository,
    @Value("\${chat.thread.idle-minutes:30}") private val idleMinutes: Long,
) {

    @Transactional
    fun append(userId: UUID, question: String, answer: String, model: String?, askedAt: Instant): Chat {
        // 같은 사용자의 동시 질문을 직렬화한다. 판정 전에 잠가야 의미가 있다.
        threadRepository.lockUser(userId)

        val latest = threadRepository.findFirstByUserIdOrderByLastQuestionAtDesc(userId)
        val thread = latest?.takeIf { it.isActiveAt(askedAt, Duration.ofMinutes(idleMinutes)) }
            ?: ChatThread(userId = userId, createdAt = askedAt, lastQuestionAt = askedAt)
        thread.markQuestioned(askedAt)

        val savedThread = threadRepository.saveAndFlush(thread)
        val threadId = requireNotNull(savedThread.id) { "스레드 저장에 실패했습니다" }

        return chatRepository.saveAndFlush(
            Chat(threadId = threadId, question = question, answer = answer, model = model, createdAt = askedAt),
        )
    }
}
