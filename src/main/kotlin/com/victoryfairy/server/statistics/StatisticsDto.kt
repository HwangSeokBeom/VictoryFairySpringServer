package com.victoryfairy.server.statistics

data class ResultCounts(
    val totalGames: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val canceled: Int,
)
