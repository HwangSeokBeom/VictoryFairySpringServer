package com.victoryfairy.server.preferences

data class PreferenceRequest(
    val favoriteTeamID: String? = null,
    val selectedSeason: Int? = null,
)

data class PreferenceDto(
    val deviceID: String,
    val favoriteTeamID: String?,
    val selectedSeason: Int,
    val createdAt: String?,
    val updatedAt: String?,
)
