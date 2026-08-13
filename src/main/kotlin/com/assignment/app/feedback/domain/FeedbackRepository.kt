package com.assignment.app.feedback.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface FeedbackRepository : JpaRepository<Feedback, Long> {
    fun findByUserId(userId: Long, pageable: Pageable): Page<Feedback>
    fun findByUserIdAndIsPositive(userId: Long, isPositive: Boolean, pageable: Pageable): Page<Feedback>
    fun findAllByIsPositive(isPositive: Boolean, pageable: Pageable): Page<Feedback>
    // findAll(pageable)은 JpaRepository가 기본 제공 — 전체 목록(ADMIN, 필터 없음)에 사용
}
