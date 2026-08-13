package com.assignment.app.domain.chat.service

import com.assignment.app.domain.chat.entity.ChatThread
import com.assignment.app.domain.chat.repository.ChatThreadRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * AI 호출 전에 이력을 읽을 스레드를 고른다. 쓰기는 하지 않는다 —
 * 스레드를 만드는 시점은 AI 응답이 성공한 뒤(ChatWriter)로 미룬다.
 */
@Service
class ChatThreadResolver(
    private val threadRepository: ChatThreadRepository,
    @Value("\${chat.thread.idle-minutes:30}") private val idleMinutes: Long,
) {

    /** 잠금 없이 읽기만 한다 — 없으면 첫 질문이라 보낼 이력도 없다. */
    @Transactional(readOnly = true)
    fun findActiveThread(userId: UUID, now: Instant): ChatThread? =
        threadRepository.findFirstByUserIdOrderByLastQuestionAtDesc(userId)
            ?.takeIf { it.isActiveAt(now, Duration.ofMinutes(idleMinutes)) }
}
