package com.assignment.app.report

import com.assignment.app.chat.domain.Chat
import com.assignment.app.chat.domain.ChatRepository
import com.assignment.app.chat.domain.ChatThread
import com.assignment.app.chat.domain.ChatThreadRepository
import com.assignment.app.config.JwtTokenProvider
import com.assignment.app.user.domain.LoginEvent
import com.assignment.app.user.domain.LoginEventRepository
import com.assignment.app.user.domain.Role
import com.assignment.app.user.domain.User
import com.assignment.app.user.domain.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 실제 PostgreSQL에 붙는 통합 테스트.
 * 대화·로그인·가입 픽스처는 API가 없거나 시각을 임의로 지정해야 하므로 리포지토리로 직접 저장한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReportIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var loginEventRepository: LoginEventRepository
    @Autowired private lateinit var chatThreadRepository: ChatThreadRepository
    @Autowired private lateinit var chatRepository: ChatRepository
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private fun uniqueEmail() = "report-${UUID.randomUUID()}@example.com"

    companion object {
        /** 테스트 픽스처 전용 값 — 실제 자격증명이 아니다. */
        private const val TEST_CREDENTIAL = "fixture-passphrase"
    }

    private fun memberToken(): String {
        val email = uniqueEmail()
        mockMvc.perform(
            post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                objectMapper.writeValueAsString(mapOf("email" to email, "password" to TEST_CREDENTIAL, "name" to "테스터")),
            ),
        ).andExpect(status().isCreated)

        val body = mockMvc.perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(
                objectMapper.writeValueAsString(mapOf("email" to email, "password" to TEST_CREDENTIAL)),
            ),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readTree(body)["accessToken"].asText()
    }

    /** admin 생성은 API가 아니라 리포지토리 직접 저장으로 — 회원가입 파라미터로 role을 받지 않는다. */
    private fun adminToken(): String {
        val hash = passwordEncoder.encode(TEST_CREDENTIAL)
        val saved = userRepository.save(
            User(email = uniqueEmail(), password = hash, name = "관리자", role = Role.ADMIN),
        )
        return jwtTokenProvider.issue(saved)
    }

    private fun saveUser(createdAt: Instant): User {
        val hash = passwordEncoder.encode(TEST_CREDENTIAL)
        return userRepository.save(
            User(email = uniqueEmail(), password = hash, name = "픽스처", role = Role.MEMBER, createdAt = createdAt),
        )
    }

    private fun saveLoginEvent(userId: UUID, createdAt: Instant) {
        loginEventRepository.save(LoginEvent(userId = userId, createdAt = createdAt))
    }

    private fun saveChat(userId: UUID, question: String, createdAt: Instant): Chat {
        val thread = chatThreadRepository.save(ChatThread(userId = userId, createdAt = createdAt, lastQuestionAt = createdAt))
        val threadId = requireNotNull(thread.id) { "저장되지 않은 스레드입니다" }
        return chatRepository.save(Chat(threadId = threadId, question = question, answer = "답", model = "gpt-4o-mini", createdAt = createdAt))
    }

    private fun activityJson(token: String) =
        objectMapper.readTree(
            mockMvc.perform(get("/api/v1/admin/activity").header("Authorization", "Bearer $token"))
                .andExpect(status().isOk).andReturn().response.contentAsString,
        )

    @Test
    fun `일반 회원이 활동 기록 API를 호출하면 403`() {
        mockMvc.perform(get("/api/v1/admin/activity").header("Authorization", "Bearer ${memberToken()}"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `일반 회원이 보고서 API를 호출하면 403`() {
        mockMvc.perform(get("/api/v1/admin/report").header("Authorization", "Bearer ${memberToken()}"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `admin으로 활동 기록을 호출하면 200과 집계값이 온다`() {
        val admin = adminToken()
        val json = activityJson(admin)
        assertTrue(json.has("from"))
        assertTrue(json.has("to"))
        assertTrue(json["signupCount"].asLong() >= 0)
        assertTrue(json["loginCount"].asLong() >= 0)
        assertTrue(json["chatCount"].asLong() >= 0)
    }

    @Test
    fun `25시간 전 데이터는 활동 기록 집계에서 빠지고 1시간 전 데이터는 포함된다`() {
        val admin = adminToken()
        val before = activityJson(admin)

        val oldUser = saveUser(Instant.now().minus(Duration.ofHours(25)))
        saveLoginEvent(requireNotNull(oldUser.id), Instant.now().minus(Duration.ofHours(25)))
        saveChat(requireNotNull(oldUser.id), "25시간 전 질문 ${UUID.randomUUID()}", Instant.now().minus(Duration.ofHours(25)))

        val afterOld = activityJson(admin)
        assertEquals(before["signupCount"].asLong(), afterOld["signupCount"].asLong())
        assertEquals(before["loginCount"].asLong(), afterOld["loginCount"].asLong())
        assertEquals(before["chatCount"].asLong(), afterOld["chatCount"].asLong())

        val recentUser = saveUser(Instant.now().minus(Duration.ofHours(1)))
        saveLoginEvent(requireNotNull(recentUser.id), Instant.now().minus(Duration.ofHours(1)))
        saveChat(requireNotNull(recentUser.id), "1시간 전 질문 ${UUID.randomUUID()}", Instant.now().minus(Duration.ofHours(1)))

        val afterRecent = activityJson(admin)
        assertEquals(before["signupCount"].asLong() + 1, afterRecent["signupCount"].asLong())
        assertEquals(before["loginCount"].asLong() + 1, afterRecent["loginCount"].asLong())
        assertEquals(before["chatCount"].asLong() + 1, afterRecent["chatCount"].asLong())
    }

    @Test
    fun `admin으로 보고서를 호출하면 200과 CSV 헤더가 온다`() {
        val admin = adminToken()
        val result = mockMvc.perform(get("/api/v1/admin/report").header("Authorization", "Bearer $admin"))
            .andExpect(status().isOk)
            .andReturn()

        assertTrue(result.response.getHeader("Content-Type")?.contains("text/csv") == true)
        assertEquals("attachment; filename=\"chat-report.csv\"", result.response.getHeader("Content-Disposition"))
        assertTrue(result.response.contentAsString.contains("chatId,threadId,userId,userEmail,userName,question,answer,model,createdAt"))
    }

    @Test
    fun `25시간 전 대화는 보고서에서 빠지고 1시간 전 대화는 작성자 정보와 함께 포함된다`() {
        val admin = adminToken()
        val recentUser = saveUser(Instant.now().minus(Duration.ofHours(1)))
        val recentQuestion = "포함되어야 할 질문 ${UUID.randomUUID()}"
        val oldQuestion = "제외되어야 할 질문 ${UUID.randomUUID()}"

        saveChat(requireNotNull(recentUser.id), recentQuestion, Instant.now().minus(Duration.ofHours(1)))
        val oldUser = saveUser(Instant.now().minus(Duration.ofHours(25)))
        saveChat(requireNotNull(oldUser.id), oldQuestion, Instant.now().minus(Duration.ofHours(25)))

        val csv = mockMvc.perform(get("/api/v1/admin/report").header("Authorization", "Bearer $admin"))
            .andExpect(status().isOk).andReturn().response.contentAsString

        assertTrue(csv.contains(recentQuestion), "최근 대화가 보고서에 없습니다: $csv")
        assertTrue(csv.contains(recentUser.email), "작성자 이메일이 보고서에 없습니다: $csv")
        assertFalse(csv.contains(oldQuestion), "25시간 전 대화가 보고서에 포함되었습니다: $csv")
    }
}
