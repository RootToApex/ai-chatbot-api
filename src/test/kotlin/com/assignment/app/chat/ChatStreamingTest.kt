package com.assignment.app.chat

import com.assignment.app.chat.domain.ChatRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ChatStreamingTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var chatRepository: ChatRepository

    private fun token(): String {
        val email = "stream-${UUID.randomUUID()}@example.com"
        mockMvc.perform(
            post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                objectMapper.writeValueAsString(mapOf("email" to email, "password" to "password123", "name" to "스트림")),
            ),
        ).andExpect(status().isCreated)

        val body = mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(
                objectMapper.writeValueAsString(mapOf("email" to email, "password" to "password123")),
            ),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readTree(body)["accessToken"].asText()
    }

    @Test
    fun `isStreaming이면 SSE로 토큰을 흘리고 스트림 종료 후 저장한다`() {
        val accessToken = token()

        val mvcResult = mockMvc.perform(
            post("/api/v1/chats").header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":"스트리밍 질문","isStreaming":true}"""),
        ).andExpect(request().asyncStarted()).andReturn()

        val response = mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk).andReturn().response
        val payload = response.contentAsString

        assertTrue(response.contentType?.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE) == true, "SSE로 응답해야 한다")
        assertTrue(payload.contains("event:token"), "토큰 이벤트가 있어야 한다")
        assertTrue(payload.contains("event:done"), "완료 이벤트가 있어야 한다")

        val threadId = objectMapper.readTree(payload.substringAfter("event:done").substringAfter("data:").substringBefore("\n"))
        val stored = chatRepository.findByThreadIdInOrderByCreatedAtAscIdAsc(listOf(threadId["threadId"].asLong()))
        assertEquals(1, stored.size, "스트림이 끝난 뒤 대화가 저장돼야 한다")
        assertTrue(stored.first().answer.isNotBlank(), "누적된 답변이 저장돼야 한다")
    }
}
