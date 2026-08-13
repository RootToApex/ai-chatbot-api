package com.assignment.app.domain.feedback.service

import com.assignment.app.domain.feedback.dto.FeedbackCreateRequest
import com.assignment.app.domain.feedback.dto.FeedbackResponse
import com.assignment.app.domain.feedback.dto.FeedbackStatusUpdateRequest
import com.assignment.app.domain.chat.repository.ChatRepository
import com.assignment.app.domain.chat.repository.ChatThreadRepository
import com.assignment.app.global.exception.ApiException
import com.assignment.app.global.common.PageParams
import com.assignment.app.global.common.PageResponse
import com.assignment.app.global.common.SortDirection
import com.assignment.app.global.config.AuthenticatedUser
import com.assignment.app.domain.feedback.entity.Feedback
import com.assignment.app.domain.feedback.repository.FeedbackRepository
import com.assignment.app.domain.feedback.entity.FeedbackStatus
import com.assignment.app.domain.user.entity.Role
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val chatRepository: ChatRepository,
    private val chatThreadRepository: ChatThreadRepository,
) {

    @Transactional
    fun create(user: AuthenticatedUser, request: FeedbackCreateRequest): FeedbackResponse {
        val chatId = requireNotNull(request.chatId) { "chatId는 필수입니다" }
        val isPositive = requireNotNull(request.isPositive) { "isPositive는 필수입니다" }

        val chat = chatRepository.findById(chatId)
            .orElseThrow { ApiException.notFound("CHAT_NOT_FOUND", "대화를 찾을 수 없습니다") }
        val thread = chatThreadRepository.findById(chat.threadId)
            .orElseThrow { ApiException.notFound("CHAT_NOT_FOUND", "대화를 찾을 수 없습니다") }

        if (user.role != Role.ADMIN && thread.userId != user.id) {
            throw ApiException.forbidden("FEEDBACK_FORBIDDEN", "본인이 생성한 대화에만 피드백을 남길 수 있습니다")
        }

        val feedback = Feedback(
            userId = user.id,
            chatId = chatId,
            isPositive = isPositive,
            status = FeedbackStatus.PENDING,
        )

        val saved = try {
            feedbackRepository.saveAndFlush(feedback)
        } catch (e: DataIntegrityViolationException) {
            // 무결성 위반을 전부 중복으로 뭉뚱그리면, 대화가 동시에 삭제돼 발생한 FK 위반까지
            // "이미 피드백을 남겼다"로 잘못 안내된다. 유일 제약 위반일 때만 409로 옮긴다.
            if (!violatesUniqueFeedback(e)) throw e
            throw ApiException.conflict("FEEDBACK_DUPLICATE", "이미 이 대화에 피드백을 남겼습니다")
        }

        return FeedbackResponse.from(saved)
    }

    @Transactional(readOnly = true)
    fun list(user: AuthenticatedUser, page: Int, size: Int, sort: String, isPositive: Boolean?): PageResponse<FeedbackResponse> {
        PageParams.validate(page, size)
        val direction = PageParams.direction(sort)
        val springDirection = if (direction == SortDirection.ASC) Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Order(springDirection, "createdAt"), Sort.Order(springDirection, "id")),
        )

        val result = if (user.role == Role.ADMIN) {
            if (isPositive != null) {
                feedbackRepository.findAllByIsPositive(isPositive, pageable)
            } else {
                feedbackRepository.findAll(pageable)
            }
        } else {
            if (isPositive != null) {
                feedbackRepository.findByUserIdAndIsPositive(user.id, isPositive, pageable)
            } else {
                feedbackRepository.findByUserId(user.id, pageable)
            }
        }

        return PageResponse.from(result, FeedbackResponse::from)
    }

    @Transactional
    fun updateStatus(user: AuthenticatedUser, id: UUID, request: FeedbackStatusUpdateRequest): FeedbackResponse {
        if (user.role != Role.ADMIN) {
            throw ApiException.forbidden("FEEDBACK_STATUS_FORBIDDEN", "관리자만 상태를 변경할 수 있습니다")
        }

        val rawStatus = requireNotNull(request.status) { "status는 필수입니다" }
        val newStatus = try {
            FeedbackStatus.valueOf(rawStatus.uppercase())
        } catch (e: IllegalArgumentException) {
            throw ApiException.badRequest("INVALID_STATUS", "status는 PENDING 또는 RESOLVED여야 합니다")
        }

        val feedback = feedbackRepository.findById(id)
            .orElseThrow { ApiException.notFound("FEEDBACK_NOT_FOUND", "피드백을 찾을 수 없습니다") }

        feedback.status = newStatus
        val saved = feedbackRepository.save(feedback)
        return FeedbackResponse.from(saved)
    }

    /** 유일 제약(user_id, chat_id) 위반인지 원인 사슬에서 제약 이름으로 판별한다. */
    private fun violatesUniqueFeedback(e: DataIntegrityViolationException): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause.message?.contains(UNIQUE_CONSTRAINT, ignoreCase = true) == true) return true
            cause = cause.cause
        }
        return false
    }

    companion object {
        private const val UNIQUE_CONSTRAINT = "uk_feedbacks_user_chat"
    }
}
