package com.victoryfairy.server.kbo.collector

import com.victoryfairy.server.kbo.importer.KboStadiumMapper
import com.victoryfairy.server.kbo.importer.KboTeamMapper
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.stereotype.Component

@Component
class KboScheduleParser {
    private val compactDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun parse(html: String, season: Int, seriesType: KboSeriesType): KboScheduleParseResult {
        val rows = Jsoup.parse(html).select("#tblScheduleList tbody tr, tbody tr, tr")
        val warnings = mutableListOf<String>()
        val games = mutableListOf<KboCollectedGameDto>()
        val gameCountByMatchup = mutableMapOf<String, Int>()
        var currentDate: LocalDate? = null
        var skipped = 0

        rows.forEachIndexed { index, row ->
            val dayCell = row.selectFirst("td.day")
            if (dayCell != null) currentDate = parseDate(dayCell.text(), season)

            if (row.selectFirst("td.play") == null) return@forEachIndexed

            val game = parseGameRow(row, currentDate, season, seriesType, gameCountByMatchup, warnings)
            if (game == null) {
                skipped += 1
                warnings += "${index + 1}번째 KBO 일정 행을 건너뛰었습니다."
            } else {
                games += game
            }
        }

        return KboScheduleParseResult(games, skipped, warnings.distinct())
    }

    private fun parseGameRow(
        row: Element,
        date: LocalDate?,
        season: Int,
        seriesType: KboSeriesType,
        gameCountByMatchup: MutableMap<String, Int>,
        warnings: MutableList<String>,
    ): KboCollectedGameDto? {
        if (date == null) return null
        val time = parseTime(row.selectFirst("td.time")?.text()) ?: return null
        val playCell = row.selectFirst("td.play") ?: return null
        val teams = playCell.select("> span").map { it.text().trim() }.filter { it.isNotBlank() }
        if (teams.size < 2) return null

        val awayTeamID = KboTeamMapper.map(teams[0]) ?: return null
        val homeTeamID = KboTeamMapper.map(teams[1]) ?: return null
        if (awayTeamID == homeTeamID) return null

        val cells = row.select("td")
        val stadiumName = KboStadiumMapper.map(cells.getOrNull(cells.size - 2)?.text(), warnings) ?: return null
        val note = cells.lastOrNull()?.text()?.trim().orEmpty()
        val scores = playCell.select("em span").mapNotNull { it.text().trim().toIntOrNull() }
        val awayScore = scores.getOrNull(0)
        val homeScore = scores.getOrNull(1)
        val status = normalizeStatus(note, homeScore, awayScore)
        val matchupKey = "${date.format(compactDateFormatter)}-${KboTeamMapper.slug(awayTeamID)}-${KboTeamMapper.slug(homeTeamID)}"
        val count = (gameCountByMatchup[matchupKey] ?: 0) + 1
        gameCountByMatchup[matchupKey] = count
        val gameCenterURL = row.select("td.relay a[href], a[href]").firstOrNull()?.attr("href")?.takeIf { it.startsWith("http") }
        val recordURL = row.select("a[href*=section=REVIEW]").firstOrNull()?.attr("href")?.takeIf { it.startsWith("http") }

        return KboCollectedGameDto(
            gameID = "$matchupKey-$count",
            date = date,
            season = season,
            seriesType = seriesType.name,
            time = time,
            homeTeamID = homeTeamID,
            awayTeamID = awayTeamID,
            homeScore = homeScore,
            awayScore = awayScore,
            stadiumName = stadiumName,
            status = status,
            kboGameCenterURL = gameCenterURL,
            kboRecordURL = recordURL,
        )
    }

    private fun parseDate(value: String, season: Int): LocalDate? {
        val match = Regex("""(\d{1,2})\.(\d{1,2})""").find(value) ?: return null
        return runCatching {
            LocalDate.of(season, match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }.getOrNull()
    }

    private fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalTime.parse(value.trim().take(5)) }.getOrNull()
    }

    fun normalizeStatus(note: String?, homeScore: Int?, awayScore: Int?): String {
        val normalized = note.orEmpty().trim().replace(Regex("\\s+"), "")
        return when {
            normalized == "경기종료" || normalized == "종료" -> "final"
            normalized == "경기전" || normalized == "예정" || normalized == "-" || normalized.isBlank() -> {
                if (homeScore != null && awayScore != null) "final" else "scheduled"
            }
            normalized.contains("연기") || normalized.contains("순연") -> "postponed"
            normalized.contains("취소") || normalized.contains("우천") || normalized.contains("그라운드") ||
                normalized.contains("폭염") || normalized.contains("미세먼지") || normalized.contains("강풍") ||
                normalized.contains("황사") -> "canceled"
            homeScore != null && awayScore != null -> "final"
            else -> "scheduled"
        }
    }
}
