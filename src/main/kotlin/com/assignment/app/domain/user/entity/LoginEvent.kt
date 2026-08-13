package com.assignment.app.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** 활동 기록의 로그인 수 집계를 위해 로그인 성공 시각을 남긴다. */
@Entity
@Table(name = "login_events")
class LoginEvent(
    @Column(name = "user_id", nullable = false)
    var userId: UUID = ZERO_UUID,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    companion object {
        /** JPA가 요구하는 기본값 자리. 실제 저장 시에는 항상 덮어써진다. */
        val ZERO_UUID: UUID = UUID(0L, 0L)
    }
}
