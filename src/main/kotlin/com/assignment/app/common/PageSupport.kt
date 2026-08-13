package com.assignment.app.common

import org.springframework.data.domain.Page

/** 목록 응답의 공통 형태. 0-base 페이지. */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <S, T> from(page: Page<S>, mapper: (S) -> T) = PageResponse(
            content = page.content.map(mapper),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
    }
}

enum class SortDirection { ASC, DESC }

/**
 * page/size는 PageRequest에 넘기기 전에 검증한다 — 음수·0이 그대로 들어가면 500이 된다.
 */
object PageParams {
    const val MAX_SIZE = 100

    fun validate(page: Int, size: Int) {
        if (page < 0) throw ApiException.badRequest("INVALID_PAGE", "page는 0 이상이어야 합니다")
        if (size < 1 || size > MAX_SIZE) {
            throw ApiException.badRequest("INVALID_SIZE", "size는 1 이상 ${MAX_SIZE} 이하여야 합니다")
        }
    }

    fun direction(sort: String): SortDirection = when (sort.lowercase()) {
        "asc" -> SortDirection.ASC
        "desc" -> SortDirection.DESC
        else -> throw ApiException.badRequest("INVALID_SORT", "sort는 asc 또는 desc여야 합니다")
    }
}
