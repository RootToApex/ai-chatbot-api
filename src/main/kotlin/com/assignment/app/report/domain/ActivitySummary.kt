package com.assignment.app.report.domain

import java.time.Instant

/**
 * 최근 24시간(rolling window) 활동 집계. 프레임워크 무의존 도메인 타입 —
 * 컨트롤러 응답 DTO(report/application)와 분리해 표현 계층 변경이 도메인에 새지 않게 한다.
 */
data class ActivitySummary(
    val from: Instant,
    val to: Instant,
    val signupCount: Long,
    val loginCount: Long,
    val chatCount: Long,
)
