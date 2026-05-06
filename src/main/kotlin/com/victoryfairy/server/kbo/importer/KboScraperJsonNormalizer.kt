package com.victoryfairy.server.kbo.importer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.kbo.NormalizedKboGame
import java.time.LocalDate
import java.time.LocalTime
import org.springframework.stereotype.Component

@Component
class KboScraperJsonNormalizer(private val objectMapper: ObjectMapper) {
    fun normalize(text: String, season: Int): NormalizationResult {
        val root = objectMapper.readTree(text)
        val records = extractRecords(root)
        val warnings = mutableListOf<String>()
        val games = mutableListOf<NormalizedKboGame>()
        val statusCounts = mutableMapOf("final" to 0, "scheduled" to 0, "canceled" to 0, "postponed" to 0)
        var skipped = 0

        records.forEachIndexed { index, node ->
            val row = normalizeRow(node, season, warnings)
            if (row == null) {
                skipped += 1
                warnings += "${index + 1}번째 행을 건너뛰었습니다."
            } else {
                games += row
                statusCounts[row.status] = (statusCounts[row.status] ?: 0) + 1
            }
        }
        return NormalizationResult(records.size, games, skipped, warnings.distinct(), statusCounts)
    }

    private fun extractRecords(root: JsonNode): List<JsonNode> {
        if (root.isArray) return root.toList()
        if (!root.isObject) throw ApiException("KBO_SCRAPED_DEV_INVALID_JSON", "KBO scraped-dev JSON 루트는 배열 또는 data 배열이어야 합니다.", 400)
        for (key in listOf("games", "items", "rows", "matches", "schedule", "results", "data")) {
            val value = root.get(key) ?: continue
            if (value.isArray) return value.toList()
            if (value.isObject) {
                val nested = runCatching { extractRecords(value) }.getOrNull()
                if (!nested.isNullOrEmpty()) return nested
            }
        }
        throw ApiException("KBO_SCRAPED_DEV_INVALID_JSON", "KBO scraped-dev JSON의 data가 배열이 아닙니다.", 400)
    }

    private fun normalizeRow(node: JsonNode, seasonFallback: Int, warnings: MutableList<String>): NormalizedKboGame? {
        val date = normalizeDate(read(node, "date", "gameDate", "game_date", "startDate", "gdate", "날짜", "일자")) ?: return null
        val homeTeamID = KboTeamMapper.map(read(node, "homeTeam", "home", "homeTeamName", "home_team", "homeName", "홈팀")) ?: return null
        val awayTeamID = KboTeamMapper.map(read(node, "awayTeam", "away", "awayTeamName", "away_team", "awayName", "원정팀", "방문팀")) ?: return null
        if (homeTeamID == awayTeamID) return null
        val stadiumName = KboStadiumMapper.map(read(node, "stadium", "stadiumName", "ballpark", "park", "place", "구장", "경기장"), warnings) ?: return null
        val homeScore = readInt(node, "homeScore", "home_score", "homeRuns", "homeRun", "홈점수", "홈스코어")
        val awayScore = readInt(node, "awayScore", "away_score", "awayRuns", "awayRun", "원정점수", "원정스코어")
        val status = normalizeStatus(read(node, "status", "gameStatus", "state", "resultStatus", "상태"), homeScore, awayScore, read(node, "cancellationReason", "cancelReason", "cancel_reason", "취소사유"))
        if (status == "final" && (homeScore == null || awayScore == null)) return null
        val gameID = read(node, "gameID", "gameId", "gameKey", "id") ?: "${date}-${KboTeamMapper.slug(homeTeamID)}-${KboTeamMapper.slug(awayTeamID)}"
        return NormalizedKboGame(
            gameID = gameID,
            date = date,
            season = readInt(node, "season") ?: seasonFallback,
            seriesType = read(node, "seriesType"),
            time = read(node, "time")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
            homeTeamID = homeTeamID,
            awayTeamID = awayTeamID,
            homeScore = homeScore,
            awayScore = awayScore,
            stadiumName = stadiumName,
            status = status,
            kboGameCenterURL = normalizeUrl(read(node, "kboGameCenterURL", "gameCenterURL", "gameCenterUrl", "gameUrl", "gameURL", "url", "link")),
            kboRecordURL = normalizeUrl(read(node, "kboRecordURL", "recordURL", "recordUrl", "boxscoreUrl", "boxscoreURL")),
            highlightTags = readTags(node),
        )
    }

    private fun read(node: JsonNode, vararg keys: String): String? = keys.asSequence()
        .mapNotNull { node.get(it) }
        .mapNotNull {
            when {
                it.isTextual -> it.asText().trim()
                it.isNumber -> it.asText()
                else -> null
            }
        }
        .firstOrNull { it.isNotBlank() }

    private fun readInt(node: JsonNode, vararg keys: String): Int? = read(node, *keys)?.trim()?.toIntOrNull()?.takeIf { it >= 0 }

    private fun normalizeDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(trimmed)) return runCatching { LocalDate.parse(trimmed) }.getOrNull()
        val compact = trimmed.replace(".", "-").replace("/", "-")
        if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(compact)) return runCatching { LocalDate.parse(compact) }.getOrNull()
        val match = Regex("(\\d{4})(\\d{2})(\\d{2})").matchEntire(trimmed) ?: return null
        return runCatching { LocalDate.parse("${match.groupValues[1]}-${match.groupValues[2]}-${match.groupValues[3]}") }.getOrNull()
    }

    fun normalizeStatus(value: String?, homeScore: Int?, awayScore: Int?, cancellationReason: String?): String {
        val explicit = value?.trim()?.lowercase()?.replace(Regex("\\s+"), "")
        if (!cancellationReason.isNullOrBlank()) return "canceled"
        return when (explicit) {
            "scheduled", "preview", "before", "예정", "경기전" -> "scheduled"
            "final", "complete", "completed", "end", "finished", "종료", "경기종료" -> "final"
            "canceled", "cancelled", "cancel", "취소", "우천취소", "경기취소" -> "canceled"
            "postponed", "delay", "delayed", "연기", "순연" -> "postponed"
            else -> if (homeScore != null && awayScore != null) "final" else "scheduled"
        }
    }

    private fun normalizeUrl(value: String?): String? = value?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    private fun readTags(node: JsonNode): List<String> {
        val value = node.get("highlightTags") ?: node.get("tags") ?: node.get("하이라이트") ?: return emptyList()
        if (value.isArray) return value.mapNotNull { it.asText(null)?.trim()?.takeIf(String::isNotBlank) }
        if (value.isTextual) return value.asText().split("|", ",").map { it.trim() }.filter { it.isNotBlank() }
        return emptyList()
    }
}

data class NormalizationResult(
    val totalRows: Int,
    val games: List<NormalizedKboGame>,
    val skipped: Int,
    val warnings: List<String>,
    val statusCounts: Map<String, Int>,
)
