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
 * 스레드 경계 판정. 외부 호출(AI)과 트랜잭션을 분리하기 위해 별도 빈으로 둔다 —
 * 같은 클래스 안에서 메서드를 나눠도 자기 호출에는 프록시 AOP가 걸리지 않는다.
 */
@Service
class ChatThreadResolver(
    private val threadRepository: ChatThreadRepository,
    @Value("\${chat.thread.idle-minutes:30}") private val idleMinutes: Long,
) {

    /** AI 호출 전 이력 조회용. 잠금 없이 읽기만 한다 — 없으면 첫 질문이라 이력도 없다. */
    @Transactional(readOnly = true)
    fun findActiveThread(userId: UUID, now: Instant): ChatThread? =
        threadRepository.findFirstByUserIdOrderByLastQuestionAtDesc(userId)?.takeIf { isWithinWindow(it, now) }

    /**
     * AI 응답을 받은 뒤 호출한다. 유저 단위 잠금 안에서 다시 판정하므로
     * 동시 질문 두 개가 각각 스레드를 만드는 일이 없다.
     * 실패한 요청이 빈 스레드를 남기지 않도록 스레드 생성 자체를 이 시점까지 미룬다.
     */
    @Transactional
    fun commitQuestion(userId: UUID, now: Instant): ChatThread {
        threadRepository.lockUser(userId)
        val latest = threadRepository.findFirstByUserIdOrderByLastQuestionAtDesc(userId)
        val thread = latest?.takeIf { isWithinWindow(it, now) }
            ?: ChatThread(userId = userId, createdAt = now, lastQuestionAt = now)
        thread.lastQuestionAt = now
        return threadRepository.saveAndFlush(thread)
    }

    /** 마지막 질문으로부터 유휴 시간이 지나지 않았으면 기존 스레드를 유지한다. */
    private fun isWithinWindow(thread: ChatThread, now: Instant): Boolean =
        Duration.between(thread.lastQuestionAt, now) <= Duration.ofMinutes(idleMinutes)
}
