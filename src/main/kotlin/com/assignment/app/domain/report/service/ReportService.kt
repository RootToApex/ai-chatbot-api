package com.assignment.app.domain.report.service

import com.assignment.app.domain.chat.repository.ChatRepository
import com.assignment.app.domain.chat.repository.ChatThreadRepository
import com.assignment.app.domain.report.dto.ActivitySummary
import com.assignment.app.domain.report.dto.ChatReportRow
import com.assignment.app.domain.report.service.ReportGenerator
import com.assignment.app.domain.user.repository.LoginEventRepository
import com.assignment.app.domain.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class ReportService(
    private val userRepository: UserRepository,
    private val loginEventRepository: LoginEventRepository,
    private val chatRepository: ChatRepository,
    private val chatThreadRepository: ChatThreadRepository,
    private val reportGenerator: ReportGenerator,
) {

    /** 요청 시점 기준 최근 24시간(rolling window) 집계 — 자정 기준이 아니다. */
    @Transactional(readOnly = true)
    fun activitySummary(): ActivitySummary {
        val to = Instant.now()
        val from = to.minus(WINDOW)
        return ActivitySummary(
            from = from,
            to = to,
            signupCount = userRepository.countByCreatedAtGreaterThanEqual(from),
            loginCount = loginEventRepository.countByCreatedAtGreaterThanEqual(from),
            chatCount = chatRepository.countByCreatedAtGreaterThanEqual(from),
        )
    }

    /**
     * 최근 24시간 대화 + 작성자 정보를 보고서 바이트로 만든다.
     * chat → thread → user 순으로 각각 일괄 조회한 뒤 Map으로 결합해 N+1을 피한다.
     */
    @Transactional(readOnly = true)
    fun generateReport(): ByteArray {
        val from = Instant.now().minus(WINDOW)
        val chats = chatRepository.findCreatedSince(from)
        if (chats.isEmpty()) return reportGenerator.generate(emptyList())

        val threadIds = chats.map { it.threadId }.distinct()
        val threadsById = chatThreadRepository.findAllById(threadIds).associateBy { it.id }

        val userIds = threadsById.values.map { it.userId }.distinct()
        val usersById = userRepository.findAllById(userIds).associateBy { it.id }

        // thread를 찾지 못한 대화(고아 행)는 userId에 UUID 기본값이 없으므로 보고서에서 건너뛴다.
        val rows = chats.mapNotNull { chat ->
            val chatId = requireNotNull(chat.id) { "저장되지 않은 대화입니다" }
            val thread = threadsById[chat.threadId] ?: return@mapNotNull null
            val user = usersById[thread.userId]
            ChatReportRow(
                chatId = chatId,
                threadId = chat.threadId,
                userId = thread.userId,
                userEmail = user?.email ?: "",
                userName = user?.name ?: "",
                question = chat.question,
                answer = chat.answer,
                model = chat.model,
                createdAt = chat.createdAt,
            )
        }
        return reportGenerator.generate(rows)
    }

    companion object {
        private val WINDOW: Duration = Duration.ofHours(24)
    }
}
