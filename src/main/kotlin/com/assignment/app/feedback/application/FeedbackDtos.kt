package com.assignment.app.feedback.application

import com.assignment.app.feedback.domain.Feedback
import com.assignment.app.feedback.domain.FeedbackStatus
import jakarta.validation.constraints.NotNull
import java.time.Instant

/** 요청 DTO는 전 필드 nullable — non-null로 받으면 필드 누락 시 400이 아니라 500이 된다. */
data class FeedbackCreateRequest(
    @field:NotNull(message = "chatId는 필수입니다")
    val chatId: Long? = null,

    @field:NotNull(message = "isPositive는 필수입니다")
    val isPositive: Boolean? = null,
)

data class FeedbackStatusUpdateRequest(
    @field:NotNull(message = "status는 필수입니다")
    val status: String? = null,
)

data class FeedbackResponse(
    val id: Long,
    val chatId: Long,
    val userId: Long,
    val isPositive: Boolean,
    val status: FeedbackStatus,
    val createdAt: Instant,
) {
    companion object {
        fun from(feedback: Feedback) = FeedbackResponse(
            id = requireNotNull(feedback.id) { "저장되지 않은 피드백입니다" },
            chatId = feedback.chatId,
            userId = feedback.userId,
            isPositive = feedback.isPositive,
            status = feedback.status,
            createdAt = feedback.createdAt,
        )
    }
}
