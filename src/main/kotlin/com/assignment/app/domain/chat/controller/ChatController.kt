package com.assignment.app.domain.chat.controller

import com.assignment.app.domain.chat.dto.ChatCreateRequest
import com.assignment.app.domain.chat.dto.ChatResponse
import com.assignment.app.domain.chat.service.ChatService
import com.assignment.app.domain.chat.dto.ThreadGroupResponse
import com.assignment.app.global.exception.ApiException
import com.assignment.app.global.exception.ErrorResponse
import com.assignment.app.global.common.PageResponse
import com.assignment.app.global.config.AuthenticatedUser
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/v1/chats")
class ChatController(
    private val chatService: ChatService,
    private val taskExecutor: AsyncTaskExecutor,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE])
    fun create(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: ChatCreateRequest,
    ): Any = if (request.isStreaming == true) {
        stream(user, request)
    } else {
        ResponseEntity.status(HttpStatus.CREATED).body(chatService.create(user.id, request))
    }

    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "desc") sort: String,
    ): PageResponse<ThreadGroupResponse> = chatService.list(user, page, size, sort)

    /**
     * SSE 응답. 첫 바이트가 나간 뒤에는 HTTP 상태코드를 바꿀 수 없으므로
     * 실패는 error 이벤트로 알리고 스트림을 닫는다.
     */
    private fun stream(user: AuthenticatedUser, request: ChatCreateRequest): SseEmitter {
        val emitter = SseEmitter(STREAM_TIMEOUT_MS)
        taskExecutor.execute {
            try {
                val chat: ChatResponse = chatService.createStreaming(user.id, request) { token ->
                    emitter.send(SseEmitter.event().name(EVENT_TOKEN).data(token))
                }
                emitter.send(SseEmitter.event().name(EVENT_DONE).data(chat))
                emitter.complete()
            } catch (e: ApiException) {
                // 클라이언트가 이미 끊겼으면 이 전송도 실패한다. 그래도 emitter는 반드시 닫아야
                // 타임아웃까지 살아남지 않는다.
                runCatching {
                    emitter.send(SseEmitter.event().name(EVENT_ERROR).data(ErrorResponse(e.status.value(), e.code, e.message)))
                }
                emitter.complete()
            } catch (e: Exception) {
                log.error("스트리밍 중 오류", e)
                emitter.completeWithError(e)
            }
        }
        return emitter
    }

    companion object {
        private const val STREAM_TIMEOUT_MS = 120_000L
        private const val EVENT_TOKEN = "token"
        private const val EVENT_DONE = "done"
        private const val EVENT_ERROR = "error"
    }
}
