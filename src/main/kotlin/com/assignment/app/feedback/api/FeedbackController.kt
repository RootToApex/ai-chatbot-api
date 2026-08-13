package com.assignment.app.feedback.api

import com.assignment.app.common.PageResponse
import com.assignment.app.config.AuthenticatedUser
import com.assignment.app.feedback.application.FeedbackCreateRequest
import com.assignment.app.feedback.application.FeedbackResponse
import com.assignment.app.feedback.application.FeedbackService
import com.assignment.app.feedback.application.FeedbackStatusUpdateRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/feedbacks")
class FeedbackController(private val feedbackService: FeedbackService) {

    @PostMapping
    fun create(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: FeedbackCreateRequest,
    ): ResponseEntity<FeedbackResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.create(user, request))

    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "desc") sort: String,
        @RequestParam(required = false) isPositive: Boolean?,
    ): PageResponse<FeedbackResponse> = feedbackService.list(user, page, size, sort, isPositive)

    @PatchMapping("/{id}/status")
    fun updateStatus(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: Long,
        @Valid @RequestBody request: FeedbackStatusUpdateRequest,
    ): FeedbackResponse = feedbackService.updateStatus(user, id, request)
}
