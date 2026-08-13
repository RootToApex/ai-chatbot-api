package com.assignment.app.chat.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 대화 묶음. OpenAI 요청에 함께 실어보내는 이력의 단위다.
 * lastQuestionAt은 질문마다 갱신되며 30분 경계 판정에 쓴다.
 */
@Entity
@Table(name = "threads")
class ChatThread(
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "last_question_at", nullable = false)
    var lastQuestionAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

@Entity
@Table(name = "chats")
class Chat(
    @Column(name = "thread_id", nullable = false)
    var threadId: Long = 0,

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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
