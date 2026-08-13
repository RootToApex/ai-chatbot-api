package com.assignment.app.feedback

import com.assignment.app.chat.domain.Chat
import com.assignment.app.chat.domain.ChatRepository
import com.assignment.app.chat.domain.ChatThread
import com.assignment.app.chat.domain.ChatThreadRepository
import com.assignment.app.config.JwtTokenProvider
import com.assignment.app.user.domain.Role
import com.assignment.app.user.domain.User
import com.assignment.app.user.domain.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

/**
 * 실제 PostgreSQL에 붙는 통합 테스트. 대화(chat)는 API가 없어 리포지토리로 직접 픽스처를 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FeedbackIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var chatThreadRepository: ChatThreadRepository
    @Autowired private lateinit var chatRepository: ChatRepository
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private fun uniqueEmail() = "fb-${UUID.randomUUID()}@example.com"

    companion object {
        /** 테스트 픽스처 전용 값 — 실제 자격증명이 아니다. */
        private const val TEST_CREDENTIAL = "fixture-passphrase"
    }

    /** 회원가입 + 로그인으로 일반 회원 (userId, token)을 만든다. */
    private fun signupMember(): Pair<UUID, String> {
        val email = uniqueEmail()
        mockMvc.perform(
            post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                objectMapper.writeValueAsString(mapOf("email" to email, "password" to TEST_CREDENTIAL, "name" to "테스터")),
            ),
        ).andExpect(status().isCreated)

        val loginBody = mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(
                objectMapper.writeValueAsString(mapOf("email" to email, "password" to TEST_CREDENTIAL)),
            ),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val token = objectMapper.readTree(loginBody)["accessToken"].asText()

        val userId = requireNotNull(userRepository.findByEmail(email)?.id) { "가입한 사용자를 찾지 못했습니다" }
        return userId to token
    }

    /** admin 생성은 API가 아니라 리포지토리 직접 저장으로 — 회원가입 파라미터로 role을 받지 않는다. */
    private fun adminToken(): String {
        val hash = passwordEncoder.encode(TEST_CREDENTIAL)
        val user = User(
            email = uniqueEmail(),
            password = hash,
            name = "관리자",
            role = Role.ADMIN,
        )
        val saved = userRepository.save(user)
        return jwtTokenProvider.issue(saved)
    }

    private fun createChat(ownerUserId: UUID): Chat {
        val thread = chatThreadRepository.save(
            ChatThread(userId = ownerUserId, createdAt = Instant.now(), lastQuestionAt = Instant.now()),
        )
        val threadId = requireNotNull(thread.id) { "저장되지 않은 스레드입니다" }
        return chatRepository.save(
            Chat(threadId = threadId, question = "질문", answer = "답변", model = null, createdAt = Instant.now()),
        )
    }

    private fun createFeedback(token: String, chatId: UUID, isPositive: Boolean = true) =
        mockMvc.perform(
            post("/api/v1/feedbacks")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("chatId" to chatId, "isPositive" to isPositive))),
        )

    @Test
    fun `남의 대화에 피드백을 생성하면 403`() {
        val (ownerId, _) = signupMember()
        val (_, otherToken) = signupMember()
        val chat = createChat(ownerId)

        createFeedback(otherToken, requireNotNull(chat.id)).andExpect(status().isForbidden)
    }

    @Test
    fun `같은 대화에 두 번 피드백을 생성하면 409`() {
        val (ownerId, token) = signupMember()
        val chat = createChat(ownerId)
        val chatId = requireNotNull(chat.id)

        createFeedback(token, chatId).andExpect(status().isCreated)
        createFeedback(token, chatId).andExpect(status().isConflict)
    }

    @Test
    fun `일반 회원이 상태 변경을 호출하면 403`() {
        val (ownerId, token) = signupMember()
        val chat = createChat(ownerId)
        val chatId = requireNotNull(chat.id)

        val createBody = createFeedback(token, chatId).andExpect(status().isCreated).andReturn().response.contentAsString
        val feedbackId = UUID.fromString(objectMapper.readTree(createBody)["id"].asText())

        mockMvc.perform(
            patch("/api/v1/feedbacks/$feedbackId/status")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("status" to "RESOLVED"))),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `ADMIN은 상태 변경이 가능하고 없는 id면 404`() {
        val (ownerId, token) = signupMember()
        val chat = createChat(ownerId)
        val chatId = requireNotNull(chat.id)
        val admin = adminToken()

        val createBody = createFeedback(token, chatId).andExpect(status().isCreated).andReturn().response.contentAsString
        val feedbackId = UUID.fromString(objectMapper.readTree(createBody)["id"].asText())

        mockMvc.perform(
            patch("/api/v1/feedbacks/$feedbackId/status")
                .header("Authorization", "Bearer $admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("status" to "resolved"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("resolved"))

        mockMvc.perform(
            patch("/api/v1/feedbacks/${UUID.randomUUID()}/status")
                .header("Authorization", "Bearer $admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("status" to "RESOLVED"))),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `목록 조회에서 남의 피드백은 보이지 않는다`() {
        val (userAId, tokenA) = signupMember()
        val (userBId, tokenB) = signupMember()
        createFeedback(tokenA, requireNotNull(createChat(userAId).id)).andExpect(status().isCreated)
        createFeedback(tokenB, requireNotNull(createChat(userBId).id)).andExpect(status().isCreated)

        val body = mockMvc.perform(
            get("/api/v1/feedbacks").header("Authorization", "Bearer $tokenA"),
        ).andExpect(status().isOk).andReturn().response.contentAsString

        val content = objectMapper.readTree(body)["content"]
        assertTrue(content.all { it["userId"].asText() == userAId.toString() })
    }

    @Test
    fun `isPositive 필터와 페이지네이션이 동작한다`() {
        val (ownerId, token) = signupMember()
        createFeedback(token, requireNotNull(createChat(ownerId).id), isPositive = true).andExpect(status().isCreated)
        createFeedback(token, requireNotNull(createChat(ownerId).id), isPositive = true).andExpect(status().isCreated)
        createFeedback(token, requireNotNull(createChat(ownerId).id), isPositive = false).andExpect(status().isCreated)

        val filteredBody = mockMvc.perform(
            get("/api/v1/feedbacks?isPositive=true").header("Authorization", "Bearer $token"),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val filtered = objectMapper.readTree(filteredBody)
        assertEquals(2, filtered["totalElements"].asInt())
        assertTrue(filtered["content"].all { it["isPositive"].asBoolean() })

        val pagedBody = mockMvc.perform(
            get("/api/v1/feedbacks?page=0&size=1").header("Authorization", "Bearer $token"),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val paged = objectMapper.readTree(pagedBody)
        assertEquals(1, paged["content"].size())
        assertEquals(3, paged["totalElements"].asInt())
        assertTrue(paged["totalPages"].asInt() >= 3)
    }

    @Test
    fun `page가 음수이거나 size가 0이면 400`() {
        val (_, token) = signupMember()

        mockMvc.perform(get("/api/v1/feedbacks?page=-1").header("Authorization", "Bearer $token"))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/v1/feedbacks?size=0").header("Authorization", "Bearer $token"))
            .andExpect(status().isBadRequest)
    }
}
