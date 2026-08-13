package com.assignment.app.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private fun uniqueEmail() = "user-${UUID.randomUUID()}@example.com"

    private fun signup(email: String, password: String = "password123", name: String = "테스터") =
        mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password, "name" to name))),
        )

    @Test
    fun `회원가입은 201과 사용자 정보를 반환하고 패스워드를 노출하지 않는다`() {
        val email = uniqueEmail()

        signup(email)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.role").value("MEMBER"))
            .andExpect(jsonPath("$.password").doesNotExist())
    }

    @Test
    fun `같은 이메일로 두 번 가입하면 409`() {
        val email = uniqueEmail()

        signup(email).andExpect(status().isCreated)
        signup(email).andExpect(status().isConflict)
    }

    @Test
    fun `필수 필드가 없으면 400`() {
        mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"no-password@example.com"}"""),
        ).andExpect(status().isBadRequest)
    }
}
