package com.assignment.app.domain.user.repository

import com.assignment.app.domain.user.entity.LoginEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface LoginEventRepository : JpaRepository<LoginEvent, UUID> {
    fun countByCreatedAtGreaterThanEqual(from: Instant): Long
}
