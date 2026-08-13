package com.assignment.app.feedback.domain

import com.fasterxml.jackson.annotation.JsonValue
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class FeedbackStatus {
    PENDING, RESOLVED;

    @JsonValue
    fun toJson(): String = name.lowercase()
}

/**
 * 대화 하나에 대한 사용자 피드백. (user_id, chat_id) 유니크 — 한 사용자는 한 대화에 하나만 남긴다.
 * 중복 방지는 exists 검사가 아니라 이 제약 위반을 잡아 409로 매핑하는 방식으로 한다(동시 요청 대비).
 */
@Entity
@Table(name = "feedbacks")
class Feedback(
    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID(0L, 0L),

    @Column(name = "chat_id", nullable = false)
    var chatId: UUID = UUID(0L, 0L),

    @Column(name = "is_positive", nullable = false)
    var isPositive: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: FeedbackStatus = FeedbackStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
}
