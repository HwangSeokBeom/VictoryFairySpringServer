package com.victoryfairy.server.profile

data class UserProfileRequest(
    val nickname: String,
    val favoriteTeamID: String,
    val profileEmoji: String? = null,
)

data class UserProfileData(
    val exists: Boolean,
    val nickname: String? = null,
    val favoriteTeamID: String? = null,
    val favoriteTeamName: String? = null,
    val profileEmoji: String? = null,
    val profileImageURL: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class ProfileImageUploadData(
    val profileImageURL: String,
)

data class ProfileImageDeleteData(
    val profileImageURL: String?,
)
