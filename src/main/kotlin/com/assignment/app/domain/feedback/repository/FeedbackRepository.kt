package com.assignment.app.domain.feedback.repository

import com.assignment.app.domain.feedback.entity.Feedback
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FeedbackRepository : JpaRepository<Feedback, UUID> {
    fun findByUserId(userId: UUID, pageable: Pageable): Page<Feedback>
    fun findByUserIdAndIsPositive(userId: UUID, isPositive: Boolean, pageable: Pageable): Page<Feedback>
    fun findAllByIsPositive(isPositive: Boolean, pageable: Pageable): Page<Feedback>
    // findAll(pageable)은 JpaRepository가 기본 제공 — 전체 목록(ADMIN, 필터 없음)에 사용
}
