package com.assignment.app.user

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun countByCreatedAtGreaterThanEqual(from: Instant): Long
}
