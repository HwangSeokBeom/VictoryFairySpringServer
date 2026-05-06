package com.victoryfairy.server.kbo

import com.fasterxml.jackson.annotation.JsonInclude

data class KboGamesData(
    val date: String,
    val teamID: String?,
    val source: String,
    val sourceLabel: String,
    val items: List<KboGameResponseItem>,
    val message: String?,
)

data class KboGameResponseItem(
    val gameID: String,
    val date: String,
    val season: Int,
    val homeTeamID: String,
    val awayTeamID: String,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val stadiumName: String,
    val status: String,
    val winnerTeamID: String?,
    val resultSummary: String?,
    val highlightTags: List<String>,
    val source: String,
    val sourceLabel: String,
    val officialLinks: Map<String, String>,
    val attendanceSuggestion: AttendanceSuggestion?,
)

data class AttendanceSuggestion(
    val favoriteTeamID: String,
    val opponentTeamID: String,
    val stadiumName: String,
    val result: String?,
    val ourScore: Int?,
    val opponentScore: Int?,
    val scoreText: String,
    val matchupText: String,
    val shortMemo: String,
    val diaryTemplate: String?,
    val highlightTags: List<String>,
)

data class KboStandingsData(
    val season: Int,
    val source: String = SCRAPED_DEV_SOURCE,
    val sourceLabel: String = SCRAPED_DEV_SOURCE_LABEL,
    @field:JsonInclude(JsonInclude.Include.ALWAYS)
    val updatedAt: String? = null,
    val items: List<KboStandingsItem> = emptyList(),
    val message: String? = null,
)

data class KboStandingsItem(
    val rank: Int,
    val teamID: String,
    val teamName: String,
    val shortName: String,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val winRate: Double?,
    val runsFor: Int,
    val runsAgainst: Int,
    val runDifferential: Int,
    val recentResults: List<String>,
)

data class KboScrapedDevUpdateSummary(
    val collectedCount: Int,
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
    val warnings: List<String>,
    val statusCounts: Map<String, Int>,
) {
    val totalRows: Int
        get() = collectedCount
}
