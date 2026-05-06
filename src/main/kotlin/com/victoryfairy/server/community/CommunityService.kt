package com.victoryfairy.server.community

import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.config.AppProperties
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CommunityService(private val properties: AppProperties) {
    fun list(): CommunityPostsData =
        CommunityPostsData(
            items = emptyList(),
            message = "응원톡은 준비 중입니다.",
            policyURL = properties.community.policyUrl,
        )

    fun create(request: CommunityPostRequest): CommunityPostResponse {
        if (!properties.community.enabled) {
            throw ApiException("COMMUNITY_DISABLED", "응원톡 작성은 아직 준비 중입니다.", 403)
        }

        val content = request.content.trim()
        if (content.isEmpty() || content.length > 300) {
            throw ApiException("VALIDATION_ERROR", "응원톡은 1자 이상 300자 이하로 입력해 주세요.", 400)
        }
        if (PROHIBITED_KEYWORDS.any { content.contains(it, ignoreCase = true) }) {
            throw ApiException("COMMUNITY_MODERATION_BLOCKED", "커뮤니티 정책에 따라 게시할 수 없는 표현이 포함되어 있습니다.", 400)
        }

        return CommunityPostResponse(
            item = CommunityPostItem(
                id = UUID.randomUUID().toString(),
                content = content,
                status = "pending_review",
                reportAvailable = true,
            ),
            message = "검토 대기 상태로 접수되었습니다.",
            policyURL = properties.community.policyUrl,
        )
    }

    companion object {
        private val PROHIBITED_KEYWORDS = listOf("도박", "베팅", "배당")
    }
}
