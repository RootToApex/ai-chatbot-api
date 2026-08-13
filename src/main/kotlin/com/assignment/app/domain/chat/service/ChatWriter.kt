package com.assignment.app.domain.chat.service

import com.assignment.app.domain.chat.entity.Chat
import com.assignment.app.domain.chat.repository.ChatRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** 저장만 담당하는 짧은 트랜잭션. AI 호출은 이 트랜잭션 밖에서 끝나 있어야 한다. */
@Service
class ChatWriter(private val chatRepository: ChatRepository) {

    @Transactional
    fun save(threadId: UUID, question: String, answer: String, model: String?, now: Instant): Chat =
        chatRepository.saveAndFlush(
            Chat(threadId = threadId, question = question, answer = answer, model = model, createdAt = now),
        )
}
