package com.assignment.app.chat.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ChatThreadRepository : JpaRepository<ChatThread, UUID> {
    /**
     * 같은 유저의 동시 질문을 직렬화한다. 트랜잭션 종료 시 자동 해제되는 advisory lock이며,
     * 이 잠금이 없으면 동시 요청 두 개가 각자 "최근 스레드 없음"으로 판정해 스레드가 두 개 생긴다.
     *
     * advisory lock은 정수 키만 받으므로 UUID를 해시해 쓴다. 해시가 충돌하면 서로 무관한 두 유저가
     * 잠깐 대기할 뿐이라 정확성에는 영향이 없다.
     */
    @Query(
        value = "SELECT 1 FROM pg_advisory_xact_lock(cast(hashtext(cast(:userId as text)) as bigint))",
        nativeQuery = true,
    )
    fun lockUser(@Param("userId") userId: UUID): Int

    /** 30분 경계 판정용 — 유저의 가장 최근 스레드 1건 */
    fun findFirstByUserIdOrderByLastQuestionAtDesc(userId: UUID): ChatThread?

    fun findByUserIdOrderByCreatedAtAscIdAsc(userId: UUID, pageable: Pageable): Page<ChatThread>
    fun findByUserIdOrderByCreatedAtDescIdDesc(userId: UUID, pageable: Pageable): Page<ChatThread>
    fun findAllByOrderByCreatedAtAscIdAsc(pageable: Pageable): Page<ChatThread>
    fun findAllByOrderByCreatedAtDescIdDesc(pageable: Pageable): Page<ChatThread>
}

interface ChatRepository : JpaRepository<Chat, UUID> {
    /** 스레드 그룹 조회의 2단계 — 컬렉션 fetch join + Pageable 조합을 피한다 */
    fun findByThreadIdInOrderByCreatedAtAscIdAsc(threadIds: Collection<UUID>): List<Chat>

    /** LLM에 보낼 이력 — 최근 N개를 역순으로 가져와 호출부가 뒤집는다 */
    fun findByThreadIdOrderByCreatedAtDescIdDesc(threadId: UUID, pageable: Pageable): List<Chat>

    fun countByCreatedAtGreaterThanEqual(from: Instant): Long

    @Query(
        """
        SELECT c FROM Chat c
        WHERE c.createdAt >= :from
        ORDER BY c.createdAt ASC, c.id ASC
        """,
    )
    fun findCreatedSince(@Param("from") from: Instant): List<Chat>
}
