package com.victoryfairy.server.kbo

import java.time.LocalDate
import org.springframework.data.jpa.repository.JpaRepository

interface KboGameRepository : JpaRepository<KboGameEntity, Long> {
    fun findByGameID(gameID: String): KboGameEntity?
    fun findByDateOrderByTimeAscGameIDAsc(date: LocalDate): List<KboGameEntity>
    fun findBySeasonAndStatusAndHomeScoreIsNotNullAndAwayScoreIsNotNullOrderByDateAscTimeAscGameIDAsc(
        season: Int,
        status: String,
    ): List<KboGameEntity>
    fun findTopBySeasonAndStatusAndHomeScoreIsNotNullAndAwayScoreIsNotNullOrderByUpdatedAtDesc(
        season: Int,
        status: String,
    ): KboGameEntity?
    fun findTopBySeasonOrderByUpdatedAtDesc(season: Int): KboGameEntity?
    fun findByDateAndHomeTeamIDOrDateAndAwayTeamIDOrderByTimeAscGameIDAsc(
        homeDate: LocalDate,
        homeTeamID: String,
        awayDate: LocalDate,
        awayTeamID: String,
    ): List<KboGameEntity>
}
