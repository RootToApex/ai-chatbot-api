package com.assignment.app.report.application

import com.assignment.app.report.domain.ActivitySummary
import java.time.Instant

/** GET /api/v1/admin/activity 응답 DTO. 도메인 타입(ActivitySummary)과 분리해 표현 계층 변경을 흡수한다. */
data class ActivityResponse(
    val from: Instant,
    val to: Instant,
    val signupCount: Long,
    val loginCount: Long,
    val chatCount: Long,
) {
    companion object {
        fun from(summary: ActivitySummary) = ActivityResponse(
            from = summary.from,
            to = summary.to,
            signupCount = summary.signupCount,
            loginCount = summary.loginCount,
            chatCount = summary.chatCount,
        )
    }
}
