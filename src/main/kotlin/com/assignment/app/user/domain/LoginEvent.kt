package com.assignment.app.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/** 활동 기록(R1)의 로그인 수 집계를 위해 로그인 성공 시각을 남긴다. */
@Entity
@Table(name = "login_events")
class LoginEvent(
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

interface LoginEventRepository : JpaRepository<LoginEvent, Long> {
    fun countByCreatedAtGreaterThanEqual(from: Instant): Long
}
