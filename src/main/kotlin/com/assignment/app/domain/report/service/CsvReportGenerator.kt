package com.assignment.app.domain.report.service

import com.assignment.app.domain.report.dto.ChatReportRow
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter

/**
 * CSV 보고서 생성기(RFC 4180 + Excel 수식 주입 방어).
 * - 모든 필드를 큰따옴표로 감싸고, 값 안의 큰따옴표는 두 개로 이중화한다
 * - 값이 `=`,`+`,`-`,`@`, 탭, 캐리지리턴으로 시작하면 앞에 `'`를 붙여 Excel이 수식으로 해석하지 못하게 막는다
 * - 줄바꿈은 \r\n(RFC 4180), UTF-8 BOM을 선두에 붙여 Excel에서 한글이 깨지지 않게 한다
 */
@Component
@ConditionalOnProperty(name = ["report.format"], havingValue = "csv", matchIfMissing = true)
class CsvReportGenerator : ReportGenerator {

    override val format = "csv"
    override val contentType = "text/csv; charset=UTF-8"
    override val fileExtension = "csv"

    override fun generate(rows: List<ChatReportRow>): ByteArray {
        val sb = StringBuilder()
        sb.append(BOM)
        sb.append(HEADER).append(CRLF)
        rows.forEach { row -> sb.append(toLine(row)).append(CRLF) }
        return sb.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun toLine(row: ChatReportRow): String = listOf(
        row.chatId.toString(),
        row.threadId.toString(),
        row.userId.toString(),
        row.userEmail,
        row.userName,
        row.question,
        row.answer,
        row.model ?: "",
        FORMATTER.format(row.createdAt),
    ).joinToString(",") { escape(it) }

    /**
     * 필드 하나를 CSV 규칙에 맞게 이스케이프한다.
     * 값 안의 개행은 그대로 둔다 — 인용된 필드 안의 개행은 규격상 허용되며,
     * CRLF로 정규화하면 사용자가 실제로 입력한 내용을 바꾸게 된다.
     */
    private fun escape(value: String): String {
        val guarded = if (value.isNotEmpty() && FORMULA_TRIGGERS.contains(value[0])) "'$value" else value
        return "\"" + guarded.replace("\"", "\"\"") + "\""
    }

    companion object {
        private const val HEADER = "chatId,threadId,userId,userEmail,userName,question,answer,model,createdAt"
        private const val CRLF = "\r\n"
        private const val BOM = "\uFEFF"
        private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@', '\t', '\r')
        private val FORMATTER = DateTimeFormatter.ISO_INSTANT
    }
}
