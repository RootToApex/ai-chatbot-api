package com.assignment.app.domain.chat.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** JPA가 요구하는 기본값 자리. 저장 시에는 항상 실제 값으로 덮어써진다. */
private val UNSET: UUID = UUID(0L, 0L)

/**
 * 대화 묶음. OpenAI 요청에 함께 실어보내는 이력의 단위다.
 * lastQuestionAt은 질문마다 갱신되며 30분 경계 판정에 쓴다.
 */
@Entity
@Table(name = "threads")
class ChatThread(
    @Column(name = "user_id", nullable = false)
    var userId: UUID = UNSET,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "last_question_at", nullable = false)
    var lastQuestionAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    /** 마지막 질문으로부터 유휴 시간이 지나지 않았으면 이어지는 대화로 본다. */
    fun isActiveAt(now: Instant, idle: Duration): Boolean =
        Duration.between(lastQuestionAt, now) <= idle

    /** 늦게 끝난 요청이 먼저 커밋돼 마지막 질문 시각이 과거로 되돌아가지 않게 한다. */
    fun markQuestioned(at: Instant) {
        if (at.isAfter(lastQuestionAt)) lastQuestionAt = at
    }
}

@Entity
@Table(name = "chats")
class Chat(
    @Column(name = "thread_id", nullable = false)
    var threadId: UUID = UNSET,

    @Column(nullable = false)
    var question: String = "",

    @Column(nullable = false)
    var answer: String = "",

    @Column
    var model: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
}
