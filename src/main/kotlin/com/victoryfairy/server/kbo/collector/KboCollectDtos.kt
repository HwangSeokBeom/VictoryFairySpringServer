package com.victoryfairy.server.kbo.collector

import com.victoryfairy.server.kbo.KboScrapedDevUpdateSummary
import com.victoryfairy.server.kbo.NormalizedKboGame
import java.time.LocalDate
import java.time.LocalTime

data class KboCollectRequestDto(
    val season: Int,
    val seriesType: String = KboSeriesType.REGULAR_SEASON.name,
)

data class KboScrapedDevUpdateRequestDto(
    val mode: String = "internal-collector",
    val season: Int? = null,
    val seriesType: String? = null,
)

typealias KboCollectResultDto = KboScrapedDevUpdateSummary

data class KboCollectedGameDto(
    val gameID: String,
    val date: LocalDate,
    val season: Int,
    val seriesType: String,
    val time: LocalTime?,
    val homeTeamID: String,
    val awayTeamID: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val stadiumName: String,
    val status: String,
    val kboGameCenterURL: String?,
    val kboRecordURL: String?,
) {
    fun toNormalized(): NormalizedKboGame = NormalizedKboGame(
        gameID = gameID,
        date = date,
        season = season,
        seriesType = seriesType,
        time = time,
        homeTeamID = homeTeamID,
        awayTeamID = awayTeamID,
        homeScore = homeScore,
        awayScore = awayScore,
        stadiumName = stadiumName,
        status = status,
        kboGameCenterURL = kboGameCenterURL,
        kboRecordURL = kboRecordURL,
        highlightTags = emptyList(),
    )
}

data class KboScheduleParseResult(
    val games: List<KboCollectedGameDto>,
    val skipped: Int,
    val warnings: List<String>,
)

enum class KboSeriesType(val optionValue: String) {
    PRESEASON("1"),
    REGULAR_SEASON("0,9,6"),
    POSTSEASON("3,4,5,7");

    companion object {
        fun parse(value: String?): KboSeriesType {
            if (value.isNullOrBlank()) return REGULAR_SEASON
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("지원하지 않는 KBO seriesType입니다: $value")
        }
    }
}
