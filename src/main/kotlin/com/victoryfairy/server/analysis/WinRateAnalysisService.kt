package com.victoryfairy.server.analysis

import com.victoryfairy.server.attendance.AttendanceLogDto
import com.victoryfairy.server.attendance.AttendanceLogService
import com.victoryfairy.server.teams.TeamSeed
import org.springframework.stereotype.Service
import kotlin.math.round

@Service
class WinRateAnalysisService(private val attendanceLogService: AttendanceLogService) {
    fun analyze(deviceID: String, season: Int): WinRateAnalysisData {
        val logs = attendanceLogService.list(deviceID, season, null)
        val counts = counts(logs)
        val sampleWarning = if (counts.wins + counts.losses < 3) SMALL_SAMPLE_WARNING else null

        return WinRateAnalysisData(
            season = season,
            summary = WinRateSummary(
                totalGames = logs.size,
                wins = counts.wins,
                losses = counts.losses,
                draws = counts.draws,
                canceled = counts.canceled,
                winRate = winRate(counts.wins, counts.losses),
                sampleWarning = sampleWarning,
            ),
            opponentRankings = opponentRankings(logs),
            stadiumRankings = stadiumRankings(logs),
            recentTrend = logs.take(5).map { trendLabel(it.result) },
            insights = insights(logs, counts),
        )
    }

    private fun opponentRankings(logs: List<AttendanceLogDto>): List<OpponentWinRateRanking> =
        logs.groupBy { it.opponentTeamID }
            .map { (teamID, teamLogs) ->
                val counts = counts(teamLogs)
                OpponentWinRateRanking(
                    teamID = teamID,
                    teamName = TeamSeed.find(teamID)?.name ?: teamID,
                    games = teamLogs.size,
                    wins = counts.wins,
                    losses = counts.losses,
                    draws = counts.draws,
                    winRate = winRate(counts.wins, counts.losses),
                )
            }
            .sortedWith(compareByDescending<OpponentWinRateRanking> { it.winRate }.thenByDescending { it.games }.thenBy { it.teamName })

    private fun stadiumRankings(logs: List<AttendanceLogDto>): List<StadiumWinRateRanking> =
        logs.groupBy { it.stadiumName }
            .map { (stadiumName, stadiumLogs) ->
                val counts = counts(stadiumLogs)
                StadiumWinRateRanking(
                    stadiumName = stadiumName,
                    games = stadiumLogs.size,
                    wins = counts.wins,
                    losses = counts.losses,
                    draws = counts.draws,
                    winRate = winRate(counts.wins, counts.losses),
                )
            }
            .sortedWith(compareByDescending<StadiumWinRateRanking> { it.winRate }.thenByDescending { it.games }.thenBy { it.stadiumName })

    private fun insights(logs: List<AttendanceLogDto>, counts: WinRateCounts): List<WinRateInsight> {
        val recent = logs.firstOrNull()
        val body = when (recent?.result) {
            "win" -> "최근 직관은 승리였어요. 좋은 흐름을 다음 기록에서도 이어가 봐요."
            "loss" -> "최근 직관은 패배였어요. 다음 기록에서 흐름을 바꿔봐요."
            "draw" -> "최근 직관은 무승부였어요. 승패보다 현장의 기억을 함께 남겨봐요."
            "canceled" -> "최근 직관 예정 경기는 취소였어요. 다음 경기 기록을 기다려봐요."
            else -> "아직 직관 기록이 없어요. 첫 기록을 남기면 흐름을 볼 수 있어요."
        }
        val items = mutableListOf(WinRateInsight("최근 흐름", body))
        if (counts.wins + counts.losses < 3) {
            items += WinRateInsight("표본 안내", SMALL_SAMPLE_WARNING)
        }
        return items
    }

    private fun counts(logs: List<AttendanceLogDto>): WinRateCounts = WinRateCounts(
        wins = logs.count { it.result == "win" },
        losses = logs.count { it.result == "loss" },
        draws = logs.count { it.result == "draw" },
        canceled = logs.count { it.result == "canceled" },
    )

    private fun winRate(wins: Int, losses: Int): Double {
        val denominator = wins + losses
        if (denominator == 0) return 0.0
        return round(wins.toDouble() / denominator * 1000) / 1000
    }

    private fun trendLabel(result: String): String = when (result) {
        "win" -> "W"
        "loss" -> "L"
        "draw" -> "D"
        "canceled" -> "C"
        else -> result.uppercase()
    }

    private data class WinRateCounts(
        val wins: Int,
        val losses: Int,
        val draws: Int,
        val canceled: Int,
    )

    companion object {
        const val SMALL_SAMPLE_WARNING = "아직 표본이 적어 재미용으로만 봐주세요."
    }
}
