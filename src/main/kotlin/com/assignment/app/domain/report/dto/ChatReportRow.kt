package com.assignment.app.domain.report.dto

import java.time.Instant
import java.util.UUID

/**
 * 대화 보고서 한 행. 출력 포맷과 무관한 순수 데이터 —
 * CSV 외 포맷(예: XLSX)의 구현체도 그대로 재사용한다.
 */
data class ChatReportRow(
    val chatId: UUID,
    val threadId: UUID,
    val userId: UUID,
    val userEmail: String,
    val userName: String,
    val question: String,
    val answer: String,
    val model: String?,
    val createdAt: Instant,
)
