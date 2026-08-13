package com.assignment.app.domain.report.controller

import com.assignment.app.global.exception.ApiException
import com.assignment.app.global.config.AuthenticatedUser
import com.assignment.app.domain.report.dto.ActivityResponse
import com.assignment.app.domain.report.service.ReportService
import com.assignment.app.domain.report.service.ReportGenerator
import com.assignment.app.domain.user.entity.Role
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Security 설정이 이미 관리자 경로를 ADMIN 역할로 제한하지만, 방어적으로 한 번 더 확인한다. */
@RestController
@RequestMapping("/api/v1/admin")
class AdminReportController(
    private val reportService: ReportService,
    private val reportGenerator: ReportGenerator,
) {

    @GetMapping("/activity")
    fun activity(@AuthenticationPrincipal user: AuthenticatedUser): ActivityResponse {
        requireAdmin(user)
        return ActivityResponse.from(reportService.activitySummary())
    }

    @GetMapping("/report")
    fun report(@AuthenticationPrincipal user: AuthenticatedUser): ResponseEntity<ByteArray> {
        requireAdmin(user)
        val body = reportService.generateReport()
        val filename = "chat-report.${reportGenerator.fileExtension}"
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, reportGenerator.contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .body(body)
    }

    private fun requireAdmin(user: AuthenticatedUser) {
        if (user.role != Role.ADMIN) throw ApiException.forbidden("FORBIDDEN", "관리자만 접근할 수 있습니다")
    }
}
