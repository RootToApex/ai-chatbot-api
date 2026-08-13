package com.assignment.app.domain.user.service

import com.assignment.app.domain.user.dto.LoginRequest
import com.assignment.app.domain.user.dto.LoginResponse
import com.assignment.app.domain.user.dto.SignupRequest
import com.assignment.app.domain.user.dto.UserResponse
import com.assignment.app.global.exception.ApiException
import com.assignment.app.global.config.JwtTokenProvider
import com.assignment.app.domain.user.entity.LoginEvent
import com.assignment.app.domain.user.repository.LoginEventRepository
import com.assignment.app.domain.user.entity.Role
import com.assignment.app.domain.user.entity.User
import com.assignment.app.domain.user.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val loginEventRepository: LoginEventRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
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
            // 무결성 위반을 전부 중복으로 단정하면, 길이 초과 같은 다른 위반까지
            // "이미 사용 중인 이메일"로 잘못 안내된다. 이메일 유일 제약일 때만 409로 옮긴다.
            if (!violatesEmailUnique(e)) throw e
            throw ApiException.conflict("EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다")
        }
        return UserResponse.from(saved)
    }

    /**
     * 이메일 존재 여부와 패스워드 불일치를 구분하지 않는다 — 가입 여부가 새어나가지 않게.
     * 로그인 성공은 login_events에 남긴다(활동 기록 집계의 유일한 근거).
     */
    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        val email = requireField(request.email, "email").trim().lowercase()
        val user = userRepository.findByEmail(email)
        val matches = user != null && passwordEncoder.matches(requireField(request.password, "password"), user.password)
        if (user == null || !matches) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "이메일 또는 패스워드가 올바르지 않습니다")
        }
        loginEventRepository.save(LoginEvent(userId = requireNotNull(user.id)))
        return LoginResponse(accessToken = jwtTokenProvider.issue(user))
    }

    /** 이메일 유일 제약 위반인지 원인 사슬에서 제약 이름으로 판별한다. */
    private fun violatesEmailUnique(e: DataIntegrityViolationException): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause.message?.contains(EMAIL_UNIQUE_CONSTRAINT, ignoreCase = true) == true) return true
            cause = cause.cause
        }
        return false
    }

    private fun requireField(value: String?, field: String): String =
        value ?: throw ApiException.badRequest("VALIDATION_FAILED", "$field 값이 필요합니다")

    companion object {
        private const val EMAIL_UNIQUE_CONSTRAINT = "uk_users_email"
    }
}
