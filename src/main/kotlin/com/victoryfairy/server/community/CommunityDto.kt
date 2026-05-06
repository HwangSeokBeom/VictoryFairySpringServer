package com.victoryfairy.server.community

import com.fasterxml.jackson.annotation.JsonInclude

data class CommunityPostsData(
    val enabled: Boolean,
    val items: List<CommunityPostItem>,
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val message: String?,
    val policyURL: String,
)

data class CommunityPostRequest(
    val teamID: String? = null,
    val content: String,
)

data class CommunityPostItem(
    val id: String,
    val teamID: String,
    val teamName: String,
    val authorID: String,
    val authorDisplayName: String,
    val authorProfileEmoji: String? = null,
    val authorProfileImageURL: String? = null,
    val content: String,
    val createdAt: String,
    val likeCount: Int,
    val reportCount: Int,
    val status: String,
)

data class CommunityPostResponse(
    val item: CommunityPostItem,
    val policyURL: String,
    val message: String? = null,
)

data class CommunityReportRequest(
    val reason: String? = null,
)

data class CommunityBlockData(
    val blockedAuthorID: String,
    val message: String,
)

data class CommunityBlockedUsersData(
    val items: List<CommunityBlockedUserItem>,
)

data class CommunityBlockedUserItem(
    val authorID: String,
    val authorDisplayName: String,
    val blockedAt: String,
)
