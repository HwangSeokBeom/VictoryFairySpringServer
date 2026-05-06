package com.victoryfairy.server.community

import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.config.AppProperties
import com.victoryfairy.server.profile.UserProfileService
import com.victoryfairy.server.teams.TeamSeed
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CommunityService(
    private val properties: AppProperties,
    private val postRepository: CommunityPostRepository,
    private val reportRepository: CommunityReportRepository,
    private val blockRepository: CommunityBlockRepository,
    private val userProfileService: UserProfileService,
) {
    fun list(deviceID: String?): CommunityPostsData {
        if (!properties.community.enabled) {
            return CommunityPostsData(
                enabled = false,
                items = emptyList(),
                message = "응원톡은 준비 중입니다.",
                policyURL = communityPolicyUrl(),
            )
        }

        val blockedAuthorIDs = if (properties.community.blockEnabled && deviceID != null) {
            blockRepository.findByBlockerDeviceIDOrderByCreatedAtDesc(deviceID).map { it.blockedAuthorID }.toSet()
        } else {
            emptySet()
        }
        val items = postRepository.findByStatusOrderByCreatedAtDesc(STATUS_VISIBLE)
            .map(::toItem)
            .filterNot { it.authorID in blockedAuthorIDs }
        return CommunityPostsData(
            enabled = true,
            items = items,
            message = if (items.isEmpty()) "아직 응원톡이 없어요. 첫 응원을 남겨보세요." else null,
            policyURL = communityPolicyUrl(),
        )
    }

    @Transactional
    fun create(deviceID: String, request: CommunityPostRequest): CommunityPostResponse {
        if (!properties.community.enabled) {
            throw ApiException("COMMUNITY_DISABLED", "응원톡 작성은 아직 준비 중입니다.", 403)
        }

        val profile = userProfileService.findEntity(deviceID)
        if (properties.community.postsRequireProfile && profile == null) {
            throw ApiException("PROFILE_REQUIRED", "응원톡을 작성하려면 프로필을 먼저 만들어 주세요.", 403)
        }

        val content = request.content.trim()
        if (content.isEmpty() || content.length > 300) {
            throw ApiException("VALIDATION_ERROR", "응원톡은 1자 이상 300자 이하로 입력해 주세요.", 400)
        }
        if (isRejectedByModeration(content)) {
            throw ApiException("COMMUNITY_CONTENT_REJECTED", "건강한 응원 문화를 위해 문구를 다시 확인해 주세요.", 400)
        }

        val teamID = request.teamID?.trim()?.takeIf { it.isNotEmpty() } ?: profile?.favoriteTeamID
            ?: throw ApiException("VALIDATION_ERROR", "teamID가 필요합니다.")
        TeamSeed.find(teamID) ?: throw ApiException("VALIDATION_ERROR", "존재하지 않는 팀입니다.")

        val post = postRepository.save(
            CommunityPostEntity(
                teamID = teamID,
                authorDeviceID = deviceID,
                authorID = profile?.let { userProfileService.ensurePublicAuthorID(it) },
                authorDisplayName = profile?.nickname ?: "익명 응원단",
                content = content,
                status = STATUS_VISIBLE,
            ),
        )
        return CommunityPostResponse(
            item = toItem(post),
            policyURL = communityPolicyUrl(),
        )
    }

    @Transactional
    fun report(deviceID: String, id: String, request: CommunityReportRequest?): CommunityPostResponse {
        val postID = runCatching { UUID.fromString(id) }
            .getOrElse { throw ApiException("COMMUNITY_POST_NOT_FOUND", "응원톡을 찾을 수 없습니다.", 404) }
        val post = postRepository.findById(postID)
            .orElseThrow { ApiException("COMMUNITY_POST_NOT_FOUND", "응원톡을 찾을 수 없습니다.", 404) }
        val reason = validateReportReason(request?.reason)

        if (!reportRepository.existsByPostIDAndDeviceID(postID, deviceID)) {
            reportRepository.save(CommunityReportEntity(postID = postID, deviceID = deviceID, reason = reason))
            post.reportCount += 1
            if (post.reportCount >= REPORT_HIDE_THRESHOLD) {
                post.status = STATUS_HIDDEN
            }
            postRepository.save(post)
        }

        return CommunityPostResponse(
            item = toItem(post),
            policyURL = communityPolicyUrl(),
            message = "신고가 접수됐어요.",
        )
    }

    @Transactional
    fun block(deviceID: String, authorID: String): CommunityBlockData {
        if (!properties.community.blockEnabled) {
            throw ApiException("COMMUNITY_BLOCK_DISABLED", "차단 기능은 비활성화되어 있습니다.", 403)
        }

        val blockerProfile = userProfileService.findEntity(deviceID)
            ?: throw ApiException("PROFILE_REQUIRED", "프로필을 먼저 만들어 주세요.", 403)
        val blockedProfile = userProfileService.findByPublicAuthorID(authorID)
            ?: throw ApiException("AUTHOR_NOT_FOUND", "사용자를 찾을 수 없습니다.", 404)

        if (blockerProfile.deviceID == blockedProfile.deviceID || userProfileService.ensurePublicAuthorID(blockerProfile) == authorID) {
            throw ApiException("CANNOT_BLOCK_SELF", "내 응원톡은 차단할 수 없습니다.")
        }

        if (!blockRepository.existsByBlockerDeviceIDAndBlockedAuthorID(deviceID, authorID)) {
            blockRepository.save(CommunityBlockEntity(blockerDeviceID = deviceID, blockedAuthorID = authorID))
        }
        return CommunityBlockData(
            blockedAuthorID = authorID,
            message = "해당 사용자의 응원톡을 숨겼어요.",
        )
    }

    @Transactional
    fun unblock(deviceID: String, authorID: String): CommunityBlockData {
        if (!properties.community.blockEnabled) {
            throw ApiException("COMMUNITY_BLOCK_DISABLED", "차단 기능은 비활성화되어 있습니다.", 403)
        }
        userProfileService.findByPublicAuthorID(authorID)
            ?: throw ApiException("AUTHOR_NOT_FOUND", "사용자를 찾을 수 없습니다.", 404)

        blockRepository.deleteByBlockerDeviceIDAndBlockedAuthorID(deviceID, authorID)
        return CommunityBlockData(
            blockedAuthorID = authorID,
            message = "차단을 해제했어요.",
        )
    }

    fun blockedUsers(deviceID: String): CommunityBlockedUsersData {
        if (!properties.community.blockEnabled) {
            throw ApiException("COMMUNITY_BLOCK_DISABLED", "차단 기능은 비활성화되어 있습니다.", 403)
        }

        val blocks = blockRepository.findByBlockerDeviceIDOrderByCreatedAtDesc(deviceID)
        val profilesByAuthorID = userProfileService.findByPublicAuthorIDs(blocks.map { it.blockedAuthorID })
            .associateBy { it.publicAuthorID }
        return CommunityBlockedUsersData(
            items = blocks.map { block ->
                val profile = profilesByAuthorID[block.blockedAuthorID]
                CommunityBlockedUserItem(
                    authorID = block.blockedAuthorID,
                    authorDisplayName = profile?.nickname ?: "알 수 없는 사용자",
                    blockedAt = block.createdAt.toString(),
                )
            },
        )
    }

    private fun communityPolicyUrl(): String = properties.legal.communityPolicyUrl

    private fun toItem(post: CommunityPostEntity): CommunityPostItem {
        val team = TeamSeed.find(post.teamID)
        val profile = userProfileService.findEntity(post.authorDeviceID)
        val authorID = post.authorID
            ?: profile?.let { userProfileService.ensurePublicAuthorID(it) }
            ?: "author_unknown"
        return CommunityPostItem(
            id = post.id.toString(),
            teamID = post.teamID,
            teamName = team?.name ?: post.teamID,
            authorID = authorID,
            authorDisplayName = profile?.nickname ?: post.authorDisplayName,
            authorProfileEmoji = profile?.profileEmoji,
            authorProfileImageURL = userProfileService.profileImageURL(profile),
            content = post.content,
            createdAt = post.createdAt.toString(),
            likeCount = post.likeCount,
            reportCount = post.reportCount,
            status = post.status,
        )
    }

    private fun validateReportReason(reason: String?): String {
        val normalized = reason?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "other"
        if (normalized !in REPORT_REASONS) {
            throw ApiException("VALIDATION_ERROR", "신고 사유를 확인해 주세요.")
        }
        return normalized
    }

    private fun isRejectedByModeration(content: String): Boolean {
        val normalized = content.lowercase().replace(Regex("\\s+"), "")
        return PROHIBITED_KEYWORDS.any { normalized.contains(it) } ||
            PHONE_PATTERN.containsMatchIn(content) ||
            EMAIL_PATTERN.containsMatchIn(content) ||
            RESIDENT_NUMBER_PATTERN.containsMatchIn(content)
    }

    companion object {
        private const val STATUS_VISIBLE = "visible"
        private const val STATUS_HIDDEN = "hidden"
        private const val REPORT_HIDE_THRESHOLD = 3
        private val REPORT_REASONS = setOf(
            "abuse",
            "hate",
            "privacy",
            "gambling",
            "copyright",
            "impersonation",
            "spam",
            "other",
        )
        private val PROHIBITED_KEYWORDS = listOf(
            "시발",
            "씨발",
            "병신",
            "새끼",
            "좆",
            "꺼져",
            "죽어",
            "혐오",
            "도박",
            "베팅",
            "배당",
            "토토",
            "바카라",
            "카지노",
            "머니라인",
            "moneyline",
            "odds",
            "티켓판매",
            "티켓양도",
            "표판매",
            "표양도",
            "입금",
            "계좌",
        )
        private val PHONE_PATTERN = Regex("""01[016789][-\s]?\d{3,4}[-\s]?\d{4}""")
        private val EMAIL_PATTERN = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
        private val RESIDENT_NUMBER_PATTERN = Regex("""\d{6}[-\s]?[1-4]\d{6}""")
    }
}
