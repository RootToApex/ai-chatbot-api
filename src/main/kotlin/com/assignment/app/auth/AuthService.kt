package com.assignment.app.auth

import com.assignment.app.common.ApiException
import com.assignment.app.user.Role
import com.assignment.app.user.User
import com.assignment.app.user.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    /**
     * role은 요청에서 받지 않는다 — 공개 API로 관리자 권한을 탈취할 수 있기 때문.
     * 중복 검사는 exists가 아니라 DB unique 제약 위반을 409로 매핑한다(동시 가입 요청에서 exists는 뚫린다).
     */
    @Transactional
    fun signup(request: SignupRequest): UserResponse {
        val email = requireField(request.email, "email").trim().lowercase()
        val hash = passwordEncoder.encode(requireField(request.password, "password"))
        val user = User(
            email = email,
            password = hash,
            name = requireField(request.name, "name").trim(),
            role = Role.MEMBER,
        )
        val saved = try {
            userRepository.saveAndFlush(user)
        } catch (e: DataIntegrityViolationException) {
            throw ApiException.conflict("EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다")
        }
        return UserResponse.from(saved)
    }

    private fun requireField(value: String?, field: String): String =
        value ?: throw ApiException.badRequest("VALIDATION_FAILED", "$field 값이 필요합니다")
}
