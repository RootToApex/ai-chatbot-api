package com.assignment.app.domain.user.entity

import com.fasterxml.jackson.annotation.JsonValue
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * 역할. 코드 안에서는 관례대로 대문자를 쓰고, 외부 계약(JSON)에는 소문자로 노출한다.
 * DB에는 @Enumerated(STRING)으로 대문자가 저장된다.
 */
enum class Role {
    MEMBER,
    ADMIN,
    ;

    @JsonValue
    fun toJson(): String = name.lowercase()
}

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true)
    var email: String = "",

    @Column(nullable = false)
    var password: String = "",

    @Column(nullable = false)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role = Role.MEMBER,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
}
