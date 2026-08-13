package com.assignment.app.chat.application

import com.assignment.app.chat.domain.Chat
import com.assignment.app.chat.domain.ChatThread
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class ChatCreateRequest(
    @field:NotBlank(message = "질문은 필수입니다")
    @field:Size(max = 4000, message = "질문은 4000자를 넘을 수 없습니다")
    val question: String? = null,

    /** true면 SSE로 응답한다 */
    val isStreaming: Boolean? = null,

    /** 지정 시 해당 모델로 생성한다 */
    val model: String? = null,
)

data class ChatResponse(
    val id: UUID,
    val threadId: UUID,
    val question: String,
    val answer: String,
    val model: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(chat: Chat) = ChatResponse(
            id = requireNotNull(chat.id) { "저장되지 않은 대화입니다" },
            threadId = chat.threadId,
            question = chat.question,
            answer = chat.answer,
            model = chat.model,
            createdAt = chat.createdAt,
        )
    }
}

/** 대화 목록은 스레드 단위로 그룹화해 응답한다. */
data class ThreadGroupResponse(
    val threadId: UUID,
    val userId: UUID,
    val createdAt: Instant,
    val lastQuestionAt: Instant,
    val chats: List<ChatResponse>,
) {
    companion object {
        fun of(thread: ChatThread, chats: List<Chat>) = ThreadGroupResponse(
            threadId = requireNotNull(thread.id) { "저장되지 않은 스레드입니다" },
            userId = thread.userId,
            createdAt = thread.createdAt,
            lastQuestionAt = thread.lastQuestionAt,
            chats = chats.map(ChatResponse::from),
        )
    }
}
