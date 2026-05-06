package com.victoryfairy.server.statistics

import com.victoryfairy.server.attendance.AttendanceLogDto
import com.victoryfairy.server.attendance.AttendanceLogService
import com.victoryfairy.server.teams.TeamSeed
import org.springframework.stereotype.Service
import kotlin.math.round

@Service
class StatisticsService(private val attendanceLogService: AttendanceLogService) {
    fun summary(deviceID: String, season: Int): Map<String, Any?> {
        val logs = attendanceLogService.list(deviceID, season, null)
        val counts = count(logs)
        val scored = logs.filter { it.result != "canceled" && it.ourScore != null && it.opponentScore != null }
        return mapOf(
            "season" to season,
            "totalGames" to counts.totalGames,
            "wins" to counts.wins,
            "losses" to counts.losses,
            "draws" to counts.draws,
            "canceled" to counts.canceled,
            "winRate" to winRate(counts.wins, counts.losses),
            "averageScored" to scored.mapNotNull { it.ourScore }.averageOrNull(1),
            "averageAllowed" to scored.mapNotNull { it.opponentScore }.averageOrNull(1),
            "currentStreakText" to currentStreak(logs),
            "recentResults" to logs.take(5).map { it.result },
        )
    }

    fun stadiums(deviceID: String, season: Int): Map<String, Any> {
        val items = attendanceLogService.list(deviceID, season, null).groupBy { it.stadiumName }.map { (stadium, logs) ->
            val counts = count(logs)
            mapOf("stadiumName" to stadium, "visitCount" to logs.size, "wins" to counts.wins, "losses" to counts.losses, "draws" to counts.draws, "canceled" to counts.canceled, "winRate" to winRate(counts.wins, counts.losses))
        }
        return mapOf("season" to season, "stadiums" to items)
    }

    fun opponents(deviceID: String, season: Int): Map<String, Any> {
        val items = attendanceLogService.list(deviceID, season, null).groupBy { it.opponentTeamID }.map { (teamID, logs) ->
            val counts = count(logs)
            mapOf("opponentTeamID" to teamID, "teamName" to (TeamSeed.find(teamID)?.name ?: teamID), "matchCount" to logs.size, "wins" to counts.wins, "losses" to counts.losses, "draws" to counts.draws, "canceled" to counts.canceled, "winRate" to winRate(counts.wins, counts.losses))
        }
        return mapOf("season" to season, "opponents" to items)
    }

    fun count(logs: List<AttendanceLogDto>): ResultCounts = ResultCounts(logs.size, logs.count { it.result == "win" }, logs.count { it.result == "loss" }, logs.count { it.result == "draw" }, logs.count { it.result == "canceled" })

    fun winRate(wins: Int, losses: Int): Double? = if (wins + losses == 0) null else roundTo(wins.toDouble() / (wins + losses), 3)

    private fun currentStreak(logs: List<AttendanceLogDto>): String {
        val first = logs.firstOrNull() ?: return "연승/연패 없음"
        if (first.result == "draw" || first.result == "canceled") return "연승/연패 없음"
        val count = logs.takeWhile { it.result == first.result }.size
        return if (first.result == "win") "${count}연승" else "${count}연패"
    }

    private fun List<Int>.averageOrNull(digits: Int): Double? = if (isEmpty()) null else roundTo(average(), digits)

    private fun roundTo(value: Double, digits: Int): Double {
        val scale = Math.pow(10.0, digits.toDouble())
        return round(value * scale) / scale
    }
}
