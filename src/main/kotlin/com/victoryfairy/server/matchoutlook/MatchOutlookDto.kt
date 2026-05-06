package com.victoryfairy.server.matchoutlook

data class MatchOutlookRequest(
    val favoriteTeamID: String,
    val opponentTeamID: String,
    val date: String,
    val stadiumName: String? = null,
)

data class MatchOutlookData(
    val title: String,
    val summary: String,
    val points: List<MatchOutlookPoint>,
    val newsReferences: List<MatchOutlookNewsReference>,
    val confidenceLabel: String,
    val generatedBy: String,
    val disclaimer: String,
)

data class MatchOutlookPoint(
    val title: String,
    val body: String,
)

data class MatchOutlookNewsReference(
    val title: String,
    val sourceName: String,
    val url: String,
)
