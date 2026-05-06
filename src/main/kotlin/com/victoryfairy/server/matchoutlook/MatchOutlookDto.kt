package com.victoryfairy.server.matchoutlook

data class MatchOutlookRequest(
    val favoriteTeamID: String,
    val opponentTeamID: String,
    val date: String,
    val stadiumName: String,
)

data class MatchOutlookData(
    val title: String,
    val summary: String,
    val points: List<String>,
    val confidenceLabel: String,
    val disclaimer: String,
)
