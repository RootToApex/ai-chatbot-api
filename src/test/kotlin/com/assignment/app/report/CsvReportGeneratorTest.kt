package com.assignment.app.report

import com.assignment.app.report.domain.ChatReportRow
import com.assignment.app.report.infrastructure.CsvReportGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

/** 순수 단위 테스트 — Spring 컨텍스트 없이 CsvReportGenerator만 검증한다. */
class CsvReportGeneratorTest {

    private val generator = CsvReportGenerator()

    private fun row(question: String, answer: String = "답") = ChatReportRow(
        chatId = UUID.randomUUID(),
        threadId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        userEmail = "user@example.com",
        userName = "테스터",
        question = question,
        answer = answer,
        model = "gpt-4o-mini",
        createdAt = Instant.parse("2026-08-13T00:00:00Z"),
    )

    private fun decode(bytes: ByteArray) = String(bytes, StandardCharsets.UTF_8)

    @Test
    fun `쉼표가 든 값은 큰따옴표로 감싸져 컬럼이 깨지지 않는다`() {
        val csv = decode(generator.generate(listOf(row("안녕, 반가워요"))))
        assertTrue(csv.contains("\"안녕, 반가워요\""))
    }

    @Test
    fun `값 안의 큰따옴표는 이중화된다`() {
        val csv = decode(generator.generate(listOf(row("그는 \"안녕\"이라고 말했다"))))
        assertTrue(csv.contains("\"그는 \"\"안녕\"\"이라고 말했다\""))
    }

    @Test
    fun `줄바꿈이 든 값도 필드 하나로 유지된다`() {
        val csv = decode(generator.generate(listOf(row("첫줄\n둘째줄"))))
        assertTrue(csv.contains("\"첫줄\n둘째줄\""))
        // 레코드 구분자는 CRLF여야 한다
        assertTrue(csv.contains("\r\n"))
    }

    @Test
    fun `등호로 시작하는 값은 앞에 작은따옴표가 붙어 수식으로 해석되지 않는다`() {
        val csv = decode(generator.generate(listOf(row("=cmd()"))))
        assertTrue(csv.contains("\"'=cmd()\""))
    }

    @Test
    fun `plus minus at 탭 캐리지리턴으로 시작하는 값도 수식 주입 방어가 적용된다`() {
        listOf("+1", "-1", "@SUM(A1)", "\t명령", "\r명령").forEach { trigger ->
            val csv = decode(generator.generate(listOf(row(trigger))))
            assertTrue(csv.contains("'$trigger"), "trigger=$trigger 방어 실패: $csv")
        }
    }

    @Test
    fun `일반 값은 작은따옴표가 붙지 않는다`() {
        val csv = decode(generator.generate(listOf(row("평범한 질문"))))
        assertTrue(csv.contains("\"평범한 질문\""))
        assertTrue(!csv.contains("'평범한 질문"))
    }

    @Test
    fun `헤더와 BOM이 포함된다`() {
        val bytes = generator.generate(emptyList())
        val csv = decode(bytes)
        assertTrue(csv.startsWith("﻿"))
        assertTrue(csv.contains("chatId,threadId,userId,userEmail,userName,question,answer,model,createdAt"))
    }

    @Test
    fun `콘텐츠 타입과 확장자는 csv다`() {
        assertEquals("csv", generator.format)
        assertEquals("csv", generator.fileExtension)
        assertEquals("text/csv; charset=UTF-8", generator.contentType)
    }
}
