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

    /**
     * 비동기 디스패치(SSE)에서도 필터를 태운다.
     * 기본값(true)이면 스트리밍 응답이 끝나고 디스패치가 필터 체인에 재진입할 때 인증이 비어 있어
     * 인가 필터가 401을 쓰려 하고, 이미 커밋된 응답이라 ServletException으로 터진다.
     */
    override fun shouldNotFilterAsyncDispatch(): Boolean = false

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
