package com.assignment.app.report.domain

import java.time.Instant

/**
 * 대화 보고서 한 행. 출력 포맷과 무관한 순수 데이터 —
 * CSV 외 포맷(예: XLSX)의 구현체도 그대로 재사용한다.
 */
data class ChatReportRow(
    val chatId: Long,
    val threadId: Long,
    val userId: Long,
    val userEmail: String,
    val userName: String,
    val question: String,
    val answer: String,
    val model: String?,
    val createdAt: Instant,
)

/**
 * 보고서 출력 포맷의 포트. 서비스는 이 인터페이스에만 의존한다 —
 * 새 형식은 구현체를 추가하고 빈으로 교체하는 것만으로 지원된다.
 */
interface ReportGenerator {
    val format: String
    val contentType: String
    val fileExtension: String
    fun generate(rows: List<ChatReportRow>): ByteArray
}
