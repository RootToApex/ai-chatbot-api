package com.assignment.app.chat.api

import com.assignment.app.chat.application.ChatService
import com.assignment.app.config.AuthenticatedUser
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/threads")
class ThreadController(private val chatService: ChatService) {

    @DeleteMapping("/{threadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable threadId: Long) {
        chatService.deleteThread(user, threadId)
    }
}
