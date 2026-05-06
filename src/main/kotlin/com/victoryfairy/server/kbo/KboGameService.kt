package com.victoryfairy.server.kbo

import com.fasterxml.jackson.databind.ObjectMapper
import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.teams.TeamSeed
import jakarta.transaction.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Service

@Service
class KboGameService(
    private val repository: KboGameRepository,
    private val suggestionService: KboAttendanceSuggestionService,
    private val objectMapper: ObjectMapper,
    private val sourceDisplayPolicy: KboSourceDisplayPolicy,
) {
    fun listGames(date: LocalDate, teamID: String?): KboGamesData {
        if (teamID != null && TeamSeed.find(teamID) == null) throw ApiException("VALIDATION_ERROR", "존재하지 않는 팀입니다.")
        val games = if (teamID == null) {
            repository.findByDateOrderByTimeAscGameIDAsc(date)
        } else {
            repository.findByDateAndHomeTeamIDOrDateAndAwayTeamIDOrderByTimeAscGameIDAsc(date, teamID, date, teamID)
        }
        val items = games.map { toResponse(it, teamID) }
        val source = pickSource(items)
        return KboGamesData(
            date.toString(),
            teamID,
            source,
            sourceDisplayPolicy.label(source),
            sourceDisplayPolicy.disclosure(source),
            items,
            if (items.isEmpty()) "해당 날짜의 경기 정보를 찾지 못했습니다." else null,
        )
    }

    fun standings(season: Int): KboStandingsData {
        val games = repository.findBySeasonAndStatusAndHomeScoreIsNotNullAndAwayScoreIsNotNullOrderByDateAscTimeAscGameIDAsc(season, "final")
        val latestFinalUpdatedAt = repository
            .findTopBySeasonAndStatusAndHomeScoreIsNotNullAndAwayScoreIsNotNullOrderByUpdatedAtDesc(season, "final")
            ?.updatedAt
        if (games.isEmpty()) {
            return KboStandingsData(
                season = season,
                sourceLabel = sourceDisplayPolicy.label(SCRAPED_DEV_SOURCE),
                sourceDisclosure = sourceDisplayPolicy.disclosure(SCRAPED_DEV_SOURCE),
                updatedAt = repository.findTopBySeasonOrderByUpdatedAtDesc(season)?.updatedAt?.formatKboStandingsUpdatedAt(),
                items = emptyList(),
                message = "수집된 경기 결과가 아직 없습니다.",
            )
        }

        val rows = mutableMapOf<String, MutableStanding>()
        games.forEach { game ->
            val homeScore = game.homeScore ?: return@forEach
            val awayScore = game.awayScore ?: return@forEach
            val home = rows.getOrPut(game.homeTeamID) { MutableStanding(game.homeTeamID) }
            val away = rows.getOrPut(game.awayTeamID) { MutableStanding(game.awayTeamID) }

            home.recordRuns(homeScore, awayScore)
            away.recordRuns(awayScore, homeScore)

            when {
                homeScore > awayScore -> {
                    home.recordResult("W")
                    away.recordResult("L")
                }
                homeScore < awayScore -> {
                    home.recordResult("L")
                    away.recordResult("W")
                }
                else -> {
                    home.recordResult("D")
                    away.recordResult("D")
                }
            }
        }

        val ranked = rows.values
            .sortedWith(
                compareByDescending<MutableStanding> { it.winRateForSort }
                    .thenByDescending { it.wins }
                    .thenByDescending { it.runDifferential }
                    .thenBy { TeamSeed.require(it.teamID).name },
            )
            .mapIndexed { index, row -> row.toItem(index + 1) }

        return KboStandingsData(
            season = season,
            sourceLabel = sourceDisplayPolicy.label(SCRAPED_DEV_SOURCE),
            sourceDisclosure = sourceDisplayPolicy.disclosure(SCRAPED_DEV_SOURCE),
            updatedAt = latestFinalUpdatedAt?.formatKboStandingsUpdatedAt(),
            items = ranked,
            message = null,
        )
    }

    @Transactional
    fun seedSampleGame(): KboGameResponseItem {
        val game = upsert(
            NormalizedKboGame(
                gameID = "2026-04-16-hanwha-samsung",
                date = LocalDate.parse("2026-04-16"),
                season = 2026,
                seriesType = "REGULAR_SEASON",
                time = null,
                homeTeamID = "hanwha-eagles",
                awayTeamID = "samsung-lions",
                homeScore = 1,
                awayScore = 6,
                stadiumName = "대전 한화생명 볼파크",
                status = "final",
                kboGameCenterURL = null,
                kboRecordURL = null,
                highlightTags = emptyList(),
            )
        ).entity
        return toResponse(game, "hanwha-eagles")
    }

    @Transactional
    fun upsert(game: NormalizedKboGame): UpsertResult {
        val existing = repository.findByGameID(game.gameID)
        val home = TeamSeed.require(game.homeTeamID)
        val away = TeamSeed.require(game.awayTeamID)
        val entity = existing ?: KboGameEntity(gameID = game.gameID)
        entity.date = game.date
        entity.season = game.season
        entity.seriesType = game.seriesType
        entity.time = game.time
        entity.homeTeamID = game.homeTeamID
        entity.awayTeamID = game.awayTeamID
        entity.homeTeamName = home.name
        entity.awayTeamName = away.name
        entity.homeScore = if (game.status == "final") game.homeScore else null
        entity.awayScore = if (game.status == "final") game.awayScore else null
        entity.stadiumName = game.stadiumName
        entity.status = game.status
        entity.winnerTeamID = winnerTeamID(game.homeTeamID, game.awayTeamID, entity.homeScore, entity.awayScore)
        entity.resultSummary = resultSummary(game.homeTeamID, game.awayTeamID, entity.homeScore, entity.awayScore, game.status)
        entity.highlightTagsJson = objectMapper.writeValueAsString(game.highlightTags)
        entity.source = SCRAPED_DEV_SOURCE
        entity.sourceLabel = SCRAPED_DEV_SOURCE_LABEL
        entity.kboGameCenterURL = game.kboGameCenterURL
        entity.kboRecordURL = game.kboRecordURL
        return UpsertResult(repository.save(entity), existing == null)
    }

    private fun toResponse(game: KboGameEntity, favoriteTeamID: String?): KboGameResponseItem {
        val tags = suggestionService.readTags(game.highlightTagsJson)
        val links = linkedMapOf<String, String>()
        if (!game.kboGameCenterURL.isNullOrBlank()) links["kboGameCenterURL"] = game.kboGameCenterURL!!
        if (!game.kboRecordURL.isNullOrBlank()) links["kboRecordURL"] = game.kboRecordURL!!
        return KboGameResponseItem(
            game.gameID,
            game.date.toString(),
            game.season,
            game.homeTeamID,
            game.awayTeamID,
            game.homeTeamName,
            game.awayTeamName,
            game.homeScore,
            game.awayScore,
            game.stadiumName,
            game.status,
            game.winnerTeamID,
            game.resultSummary,
            tags,
            game.source,
            sourceDisplayPolicy.label(game.source),
            sourceDisplayPolicy.disclosure(game.source),
            links,
            favoriteTeamID?.let { suggestionService.suggestion(game, it) },
        )
    }
}

data class UpsertResult(val entity: KboGameEntity, val inserted: Boolean)

private val kboStandingsZone: ZoneId = ZoneId.of("Asia/Seoul")
private val kboStandingsUpdatedAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

private fun Instant.formatKboStandingsUpdatedAt(): String = kboStandingsUpdatedAtFormatter.format(atZone(kboStandingsZone))

private class MutableStanding(
    val teamID: String,
) {
    var games: Int = 0
    var wins: Int = 0
    var losses: Int = 0
    var draws: Int = 0
    var runsFor: Int = 0
    var runsAgainst: Int = 0
    private val results = mutableListOf<String>()

    val runDifferential: Int
        get() = runsFor - runsAgainst

    val winRate: Double?
        get() = if (wins + losses == 0) null else wins.toDouble() / (wins + losses)

    val winRateForSort: Double
        get() = winRate ?: 0.0

    fun recordRuns(forScore: Int, againstScore: Int) {
        games += 1
        runsFor += forScore
        runsAgainst += againstScore
    }

    fun recordResult(result: String) {
        when (result) {
            "W" -> wins += 1
            "L" -> losses += 1
            "D" -> draws += 1
        }
        results += result
    }

    fun toItem(rank: Int): KboStandingsItem {
        val team = TeamSeed.require(teamID)
        return KboStandingsItem(
            rank = rank,
            teamID = teamID,
            teamName = team.name,
            shortName = team.shortName,
            games = games,
            wins = wins,
            losses = losses,
            draws = draws,
            winRate = winRate,
            runsFor = runsFor,
            runsAgainst = runsAgainst,
            runDifferential = runDifferential,
            recentResults = results.takeLast(5).asReversed(),
        )
    }
}

fun pickSource(items: List<KboGameResponseItem>): String = when {
    items.isEmpty() -> "unavailable"
    items.any { it.source == SCRAPED_DEV_SOURCE } -> SCRAPED_DEV_SOURCE
    else -> items.first().source
}

fun winnerTeamID(homeTeamID: String, awayTeamID: String, homeScore: Int?, awayScore: Int?): String? = when {
    homeScore == null || awayScore == null -> null
    homeScore > awayScore -> homeTeamID
    awayScore > homeScore -> awayTeamID
    else -> null
}

fun resultSummary(homeTeamID: String, awayTeamID: String, homeScore: Int?, awayScore: Int?, status: String): String? {
    if (status != "final" || homeScore == null || awayScore == null) return null
    val home = TeamSeed.require(homeTeamID).shortName
    val away = TeamSeed.require(awayTeamID).shortName
    return when {
        homeScore > awayScore -> "${KoreanParticle.subject(home)} ${homeScore}:${awayScore}로 승리"
        awayScore > homeScore -> "${KoreanParticle.subject(away)} ${awayScore}:${homeScore}로 승리"
        else -> "${home}와 ${away}의 $homeScore:$awayScore 무승부"
    }
}
