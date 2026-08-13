package com.assignment.app.config

import com.assignment.app.user.Role
import com.assignment.app.user.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/**
 * JWT 발급·검증. 서명 키는 환경변수 JWT_SECRET에서 읽고,
 * 없으면 기동 시 임의 키를 생성한다 — 저장소에 기본 시크릿을 두지 않기 위함이며,
 * 이 경우 재기동 시 기존 토큰은 무효가 된다.
 */
@Component
class JwtTokenProvider(
    @Value("\${jwt.secret:}") configured: String,
    @Value("\${jwt.expiration-minutes:60}") private val expirationMinutes: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val key: SecretKey = if (configured.isBlank()) {
        log.warn("JWT_SECRET 미설정 — 임의 서명 키로 기동합니다(재기동 시 기존 토큰 무효)")
        Jwts.SIG.HS256.key().build()
    } else {
        Keys.hmacShaKeyFor(configured.toByteArray())
    }

    fun issue(user: User): String {
        val issuedAt = Instant.now()
        return Jwts.builder()
            .subject(requireNotNull(user.id) { "저장되지 않은 사용자입니다" }.toString())
            .claim("email", user.email)
            .claim("role", user.role.name)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plusSeconds(expirationMinutes * 60)))
            .signWith(key)
            .compact()
    }

    /** 서명·만료가 유효하지 않으면 null. 호출부가 401로 매핑한다. */
    fun parse(token: String): AuthenticatedUser? = try {
        val claims: Claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        AuthenticatedUser(
            id = claims.subject.toLong(),
            email = claims["email"] as String,
            role = Role.valueOf(claims["role"] as String),
        )
    } catch (e: JwtException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}

data class AuthenticatedUser(val id: Long, val email: String, val role: Role)
