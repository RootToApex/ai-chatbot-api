package com.assignment.app.user.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun countByCreatedAtGreaterThanEqual(from: Instant): Long
}
