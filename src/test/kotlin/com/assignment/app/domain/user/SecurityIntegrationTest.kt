package com.assignment.app.domain.user

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun memberToken(): String {
        val email = "sec-${UUID.randomUUID()}@example.com"
        mockMvc.perform(
            post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                objectMapper.writeValueAsString(mapOf("email" to email, "password" to "password123", "name" to "테스터")),
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
    fun `토큰 없이 보호된 요청을 보내면 401`() {
        mockMvc.perform(get("/api/v1/chats")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `위조된 토큰이면 401`() {
        mockMvc.perform(get("/api/v1/chats").header("Authorization", "Bearer not-a-real-token"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `일반 회원이 관리자 API를 호출하면 403`() {
        mockMvc.perform(get("/api/v1/admin/activity").header("Authorization", "Bearer ${memberToken()}"))
            .andExpect(status().isForbidden)
    }
}
