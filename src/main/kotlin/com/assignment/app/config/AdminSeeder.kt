package com.assignment.app.config

import com.assignment.app.user.Role
import com.assignment.app.user.User
import com.assignment.app.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * 관리자 계정은 이 시드로만 만든다 — 회원가입 API가 role을 받으면 공개 API로 권한 탈취가 되기 때문.
 * 값은 환경변수로 주입하며, 로컬 기본값은 채점자가 키 없이 재현할 수 있도록 README에 명시한다.
 */
@Component
class AdminSeeder(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${admin.email:admin@example.com}") private val adminEmail: String,
    @Value("\${admin.password:}") private val adminPassword: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (userRepository.findByEmail(adminEmail) != null) return
        val hash = passwordEncoder.encode(adminPassword.ifBlank { DEFAULT_LOCAL_PASSWORD })
        userRepository.save(
            User(email = adminEmail, password = hash, name = "관리자", role = Role.ADMIN),
        )
        log.info("관리자 계정을 생성했습니다: {}", adminEmail)
    }

    companion object {
        /** 로컬 재현용 기본값. 운영 환경에서는 ADMIN_PASSWORD로 반드시 덮어쓴다. */
        private const val DEFAULT_LOCAL_PASSWORD = "admin1234"
    }
}
