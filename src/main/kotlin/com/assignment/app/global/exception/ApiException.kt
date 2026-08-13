package com.assignment.app.global.exception

import org.springframework.http.HttpStatus

/** 도메인 규칙 위반을 HTTP 상태코드와 함께 표현한다. */
class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message) {
    companion object {
        fun conflict(code: String, message: String) = ApiException(HttpStatus.CONFLICT, code, message)
        fun notFound(code: String, message: String) = ApiException(HttpStatus.NOT_FOUND, code, message)
        fun forbidden(code: String, message: String) = ApiException(HttpStatus.FORBIDDEN, code, message)
        fun badRequest(code: String, message: String) = ApiException(HttpStatus.BAD_REQUEST, code, message)
        fun unauthorized(code: String, message: String) = ApiException(HttpStatus.UNAUTHORIZED, code, message)
    }
}

data class ErrorResponse(
    val status: Int,
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
)
