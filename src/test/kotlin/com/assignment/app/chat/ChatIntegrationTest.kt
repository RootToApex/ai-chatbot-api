package com.assignment.app.chat

import com.assignment.app.ai.domain.AiRole
import com.assignment.app.chat.domain.Chat
import com.assignment.app.chat.domain.ChatRepository
import com.assignment.app.chat.domain.ChatThread
import com.assignment.app.chat.domain.ChatThreadRepository
import com.assignment.app.support.FakeAiProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ChatIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var threadRepository: ChatThreadRepository
    @Autowired private lateinit var chatRepository: ChatRepository
    @Autowired private lateinit var fakeAiProvider: FakeAiProvider

    private fun newUserToken(): String {
        val email = "chat-${UUID.randomUUID()}@example.com"
        mockMvc.perform(
            post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                objectMapper.writeValueAsString(mapOf("email" to email, "password" to "password123", "name" to "대화자")),
            ),
        ).andExpect(status().isCreated)

        val body = mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(
                objectMapper.writeValueAsString(mapOf("email" to email, "password" to "password123")),
            ),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readTree(body)["accessToken"].asText()
    }

    private fun ask(token: String, question: String, model: String? = null): UUID {
        val payload = buildMap {
            put("question", question)
            if (model != null) put("model", model)
        }
        val body = mockMvc.perform(
            post("/api/v1/chats").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return UUID.fromString(objectMapper.readTree(body)["threadId"].asText())
    }

    @Test
    fun `대화를 생성하면 답변과 스레드가 함께 저장된다`() {
        val token = newUserToken()

        mockMvc.perform(
            post("/api/v1/chats").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":"안녕하세요"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isString)
            .andExpect(jsonPath("$.threadId").isString)
            .andExpect(jsonPath("$.question").value("안녕하세요"))
            .andExpect(jsonPath("$.answer").isNotEmpty)
    }

    @Test
    fun `30분 이내 재질문은 기존 스레드를 유지하고 30분이 지나면 새 스레드를 만든다`() {
        val token = newUserToken()

        val first = ask(token, "첫 질문")
        val second = ask(token, "이어지는 질문")
        assertEquals(first, second, "30분 이내 질문은 같은 스레드여야 한다")

        // 마지막 질문 시각을 31분 전으로 되돌려 유휴 경과 상황을 만든다
        val thread = threadRepository.findById(first).orElseThrow()
        thread.lastQuestionAt = Instant.now().minus(31, ChronoUnit.MINUTES)
        threadRepository.saveAndFlush(thread)

        val third = ask(token, "한참 뒤 질문")
        assertNotEquals(first, third, "30분이 지나면 새 스레드여야 한다")
    }

    @Test
    fun `provider에는 해당 스레드의 이력만 보낸다`() {
        val token = newUserToken()
        val threadId = ask(token, "질문 A")

        // 다른 유저의 대화가 섞이지 않아야 한다
        val otherToken = newUserToken()
        ask(otherToken, "남의 질문")

        ask(token, "질문 B")

        val sent = fakeAiProvider.lastRequest
        checkNotNull(sent) { "provider 요청이 기록되지 않았다" }
        val contents = sent.messages.map { it.content }
        assertTrue(contents.any { it.contains("질문 A") }, "같은 스레드의 이전 질문은 포함돼야 한다")
        assertFalse(contents.any { it.contains("남의 질문") }, "다른 유저의 대화가 섞이면 안 된다")
        assertEquals(AiRole.USER, sent.messages.last().role, "마지막 메시지는 이번 질문이어야 한다")

        val stored = chatRepository.findByThreadIdInOrderByCreatedAtAscIdAsc(listOf(threadId))
        assertEquals(2, stored.size, "대화는 스레드에 누적 저장돼야 한다")
    }

    @Test
    fun `이력은 최근 N개로 잘라서 보낸다`() {
        val token = newUserToken()
        val threadId = ask(token, "시작")

        // 이력 상한(기본 10)을 넘기도록 과거 대화를 직접 채운다
        val base = Instant.now().minus(20, ChronoUnit.MINUTES)
        repeat(15) { index ->
            chatRepository.saveAndFlush(
                Chat(
                    threadId = threadId,
                    question = "과거 질문 $index",
                    answer = "과거 답변 $index",
                    model = "fixture",
                    createdAt = base.plusSeconds(index.toLong()),
                ),
            )
        }

        ask(token, "마지막 질문")

        val sent = fakeAiProvider.lastRequest
        checkNotNull(sent)
        // 최근 10개 대화(질문·답변 2개씩) + 이번 질문 1개
        assertEquals(21, sent.messages.size, "이력은 최근 N개로 제한돼야 한다")
        assertFalse(sent.messages.any { it.content.contains("과거 질문 0") }, "오래된 대화는 잘려야 한다")
    }

    @Test
    fun `목록은 스레드 단위로 그룹화되고 남의 스레드는 보이지 않는다`() {
        val token = newUserToken()
        ask(token, "내 질문")
        val otherToken = newUserToken()
        ask(otherToken, "남의 질문")

        mockMvc.perform(get("/api/v1/chats").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].threadId").isString)
            .andExpect(jsonPath("$.content[0].chats[0].question").value("내 질문"))
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `잘못된 페이지 파라미터는 400`() {
        val token = newUserToken()

        mockMvc.perform(get("/api/v1/chats").param("page", "-1").header("Authorization", "Bearer $token"))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/v1/chats").param("size", "0").header("Authorization", "Bearer $token"))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/v1/chats").param("sort", "sideways").header("Authorization", "Bearer $token"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `남의 스레드는 삭제할 수 없고 본인 스레드를 지우면 대화도 함께 사라진다`() {
        val ownerToken = newUserToken()
        val threadId = ask(ownerToken, "지울 대화")
        val strangerToken = newUserToken()

        mockMvc.perform(delete("/api/v1/threads/$threadId").header("Authorization", "Bearer $strangerToken"))
            .andExpect(status().isForbidden)

        mockMvc.perform(delete("/api/v1/threads/$threadId").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isNoContent)

        assertTrue(chatRepository.findByThreadIdInOrderByCreatedAtAscIdAsc(listOf(threadId)).isEmpty())
    }

    @Test
    fun `없는 스레드를 삭제하면 404`() {
        val token = newUserToken()
        val missingId = UUID.randomUUID()

        mockMvc.perform(delete("/api/v1/threads/$missingId").header("Authorization", "Bearer $token"))
            .andExpect(status().isNotFound)
    }
}
