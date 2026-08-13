package com.assignment.app.global.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * ResponseEntityExceptionHandler를 상속해야 프레임워크 예외(없는 경로·타입 불일치·미지원 메서드)가
 * 최상위 Exception 핸들러에 삼켜져 500이 되지 않는다.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApi(e: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(e.status).body(ErrorResponse(e.status.value(), e.code, e.message))

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("처리되지 않은 예외", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(500, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다"))
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val details = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "잘못된 값") }
        val body = ErrorResponse(400, "VALIDATION_FAILED", "요청 값이 올바르지 않습니다", details)
        return ResponseEntity.badRequest().body(body)
    }
}
