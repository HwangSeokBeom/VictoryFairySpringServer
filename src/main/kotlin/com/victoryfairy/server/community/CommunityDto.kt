package com.victoryfairy.server.community

data class CommunityPostsData(
    val items: List<CommunityPostItem>,
    val message: String,
    val policyURL: String,
)

data class CommunityPostRequest(
    val content: String,
)

data class CommunityPostItem(
    val id: String,
    val content: String,
    val status: String,
    val reportAvailable: Boolean,
)

data class CommunityPostResponse(
    val item: CommunityPostItem,
    val message: String,
    val policyURL: String,
)
