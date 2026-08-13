package com.assignment.app.domain.report.service

import com.assignment.app.domain.report.dto.ChatReportRow


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
