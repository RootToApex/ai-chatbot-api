package com.assignment.app.user.application

import com.assignment.app.user.domain.Role
import com.assignment.app.user.domain.User
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** 요청 DTO는 전 필드 nullable — non-null로 받으면 필드 누락 시 400이 아니라 500이 된다. */
data class SignupRequest(
    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Email(message = "이메일 형식이 올바르지 않습니다")
    val email: String? = null,

    @field:NotBlank(message = "패스워드는 필수입니다")
    @field:Size(min = 8, max = 72, message = "패스워드는 8자 이상 72자 이하여야 합니다")
    val password: String? = null,

    @field:NotBlank(message = "이름은 필수입니다")
    @field:Size(max = 100, message = "이름은 100자를 넘을 수 없습니다")
    val name: String? = null,
)

data class LoginRequest(
    @field:NotBlank(message = "이메일은 필수입니다")
    val email: String? = null,

    @field:NotBlank(message = "패스워드는 필수입니다")
    val password: String? = null,
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val name: String,
    val role: Role,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User) = UserResponse(
            id = requireNotNull(user.id) { "저장되지 않은 사용자입니다" },
            email = user.email,
            name = user.name,
            role = user.role,
            createdAt = user.createdAt,
        )
    }
}

data class LoginResponse(val accessToken: String, val tokenType: String = "Bearer")
