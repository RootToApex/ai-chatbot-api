package com.assignment.app.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authorization: Bearer <JWT>를 파싱해 SecurityContext를 채운다.
 * 토큰이 없거나 유효하지 않으면 인증을 비워둔 채 통과시키고,
 * 최종 거부(401/403)는 SecurityConfig의 인가 규칙이 판단한다.
 */
@Component
class JwtAuthenticationFilter(private val jwtTokenProvider: JwtTokenProvider) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            val principal = jwtTokenProvider.parse(header.removePrefix(BEARER_PREFIX).trim())
            if (principal != null) {
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(principal, null, authorities)
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
