package com.victoryfairy.server.kbo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.victoryfairy.server.teams.TeamSeed
import org.springframework.stereotype.Service

private val resultLabels = mapOf("win" to "승", "loss" to "패", "draw" to "무", "canceled" to "취소")

@Service
class KboAttendanceSuggestionService(private val objectMapper: ObjectMapper) {
    fun suggestion(game: KboGameEntity, favoriteTeamID: String): AttendanceSuggestion {
        val isHomeFavorite = favoriteTeamID == game.homeTeamID
        val opponentTeamID = if (isHomeFavorite) game.awayTeamID else game.homeTeamID
        val favoriteTeam = TeamSeed.find(favoriteTeamID)
        val opponentTeam = TeamSeed.find(opponentTeamID)
        val matchupText = "${favoriteTeam?.shortName ?: favoriteTeamID} vs ${opponentTeam?.shortName ?: opponentTeamID}"
        val tags = readTags(game.highlightTagsJson)

        if (game.status == "canceled" || game.status == "postponed") {
            return AttendanceSuggestion(favoriteTeamID, opponentTeamID, game.stadiumName, "canceled", null, null, "취소", matchupText, "경기가 취소되었습니다.", null, tags)
        }

        val favoriteScore = if (isHomeFavorite) game.homeScore else game.awayScore
        val opponentScore = if (isHomeFavorite) game.awayScore else game.homeScore
        if (game.status == "scheduled" || favoriteScore == null || opponentScore == null) {
            return AttendanceSuggestion(favoriteTeamID, opponentTeamID, game.stadiumName, null, null, null, "경기 전", matchupText, "예정 경기입니다. 결과는 경기 후 입력해 주세요.", null, tags)
        }

        val result = when {
            favoriteScore > opponentScore -> "win"
            favoriteScore < opponentScore -> "loss"
            else -> "draw"
        }
        val scoreText = "$favoriteScore:$opponentScore ${resultLabels[result]}"
        val resultText = when (result) {
            "win" -> "승리"
            "loss" -> "패배"
            else -> "무승부"
        }
        val shortMemo = game.resultSummary?.let { "${it}했던 경기" } ?: "$scoreText 경기"
        val favoriteTeamName = favoriteTeam?.name ?: favoriteTeamID
        val opponentTeamName = opponentTeam?.name ?: opponentTeamID
        val diaryTemplate = if (result == "loss") {
            "오늘은 ${game.stadiumName}에서 ${favoriteTeamName}와 ${opponentTeamName}의 경기를 직관했다. 결과는 ${favoriteScore}:${opponentScore} 패배였지만, 경기장의 분위기와 응원은 오래 기억에 남았다."
        } else {
            "오늘은 ${game.stadiumName}에서 ${favoriteTeamName}와 ${opponentTeamName}의 경기를 직관했다. 결과는 ${favoriteScore}:${opponentScore} ${resultText}로 남았고, 경기장의 분위기와 응원도 오래 기억에 남았다."
        }

        return AttendanceSuggestion(favoriteTeamID, opponentTeamID, game.stadiumName, result, favoriteScore, opponentScore, scoreText, matchupText, shortMemo, diaryTemplate, tags)
    }

    fun readTags(json: String): List<String> = runCatching { objectMapper.readValue<List<String>>(json) }.getOrDefault(emptyList())
}
