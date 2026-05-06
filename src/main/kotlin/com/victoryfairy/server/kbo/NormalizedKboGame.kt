package com.victoryfairy.server.kbo

import java.time.LocalDate
import java.time.LocalTime

data class NormalizedKboGame(
    val gameID: String,
    val date: LocalDate,
    val season: Int,
    val seriesType: String?,
    val time: LocalTime?,
    val homeTeamID: String,
    val awayTeamID: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val stadiumName: String,
    val status: String,
    val kboGameCenterURL: String?,
    val kboRecordURL: String?,
    val highlightTags: List<String>,
)
