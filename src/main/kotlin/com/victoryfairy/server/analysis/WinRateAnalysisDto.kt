package com.victoryfairy.server.analysis

data class WinRateAnalysisData(
    val season: Int,
    val summary: WinRateSummary,
    val opponentRankings: List<OpponentWinRateRanking>,
    val stadiumRankings: List<StadiumWinRateRanking>,
    val recentTrend: List<String>,
    val insights: List<WinRateInsight>,
)

data class WinRateSummary(
    val totalGames: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val canceled: Int,
    val winRate: Double,
    val sampleWarning: String?,
)

data class OpponentWinRateRanking(
    val teamID: String,
    val teamName: String,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val winRate: Double,
)

data class StadiumWinRateRanking(
    val stadiumName: String,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val winRate: Double,
)

data class WinRateInsight(
    val title: String,
    val body: String,
)
