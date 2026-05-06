package com.victoryfairy.server.matchoutlook

import com.victoryfairy.server.attendance.AttendanceLogDto
import com.victoryfairy.server.attendance.AttendanceLogService
import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.teams.TeamSeed
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class MatchOutlookService(private val attendanceLogService: AttendanceLogService) {
    fun create(deviceID: String, request: MatchOutlookRequest): MatchOutlookData {
        val favoriteTeam = TeamSeed.find(request.favoriteTeamID)
            ?: throw ApiException("VALIDATION_ERROR", "존재하지 않는 팀입니다.")
        val opponentTeam = TeamSeed.find(request.opponentTeamID)
            ?: throw ApiException("VALIDATION_ERROR", "존재하지 않는 팀입니다.")
        if (request.favoriteTeamID == request.opponentTeamID) {
            throw ApiException("VALIDATION_ERROR", "응원팀과 상대팀은 같을 수 없습니다.")
        }

        val date = runCatching { LocalDate.parse(request.date) }
            .getOrElse { throw ApiException("VALIDATION_ERROR", "date는 yyyy-MM-dd 형식이어야 합니다.", 400) }
        val seasonLogs = attendanceLogService.list(deviceID, date.year, null)
            .filter { it.favoriteTeamID == request.favoriteTeamID }
        val opponentLogs = seasonLogs.filter { it.opponentTeamID == request.opponentTeamID }
        val stadiumLogs = seasonLogs.filter { it.stadiumName == request.stadiumName }

        return MatchOutlookData(
            title = "오늘의 관전 포인트",
            summary = "내 직관 기록과 참고용 경기 정보를 바탕으로 본 응원 포인트예요.",
            points = buildPoints(favoriteTeam.shortName, opponentTeam.shortName, request.stadiumName, seasonLogs, opponentLogs, stadiumLogs),
            confidenceLabel = "재미용",
            disclaimer = "공식 예측이나 베팅 정보가 아닙니다.",
        )
    }

    private fun buildPoints(
        favoriteTeamName: String,
        opponentTeamName: String,
        stadiumName: String,
        seasonLogs: List<AttendanceLogDto>,
        opponentLogs: List<AttendanceLogDto>,
        stadiumLogs: List<AttendanceLogDto>,
    ): List<String> {
        val points = mutableListOf<String>()
        val opponentDecided = opponentLogs.countDecided()
        if (opponentDecided < 3) {
            points += "최근 직관 기록 기준으로는 ${opponentTeamName}전 표본이 아직 적어요."
        } else {
            val counts = resultCounts(opponentLogs)
            points += "${opponentTeamName}전 직관 기록은 ${counts.wins}승 ${counts.losses}패 흐름이에요."
        }

        val stadiumDecided = stadiumLogs.countDecided()
        if (stadiumDecided < 3) {
            points += "${stadiumName} 기록을 더 쌓으면 구장별 흐름을 더 잘 볼 수 있어요."
        } else {
            val counts = resultCounts(stadiumLogs)
            points += "${stadiumName} 직관 기록은 ${counts.wins}승 ${counts.losses}패로 남아 있어요."
        }

        val seasonDecided = seasonLogs.countDecided()
        if (seasonDecided >= 3) {
            val counts = resultCounts(seasonLogs)
            points += "이번 시즌 ${favoriteTeamName} 직관 기록은 ${counts.wins}승 ${counts.losses}패예요."
        }

        return points.take(3)
    }

    private fun List<AttendanceLogDto>.countDecided(): Int = count { it.result == "win" || it.result == "loss" }

    private fun resultCounts(logs: List<AttendanceLogDto>): ResultCounts = ResultCounts(
        wins = logs.count { it.result == "win" },
        losses = logs.count { it.result == "loss" },
    )

    private data class ResultCounts(val wins: Int, val losses: Int)
}
