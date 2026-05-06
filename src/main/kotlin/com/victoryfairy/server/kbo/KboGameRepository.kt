package com.victoryfairy.server.kbo

import java.time.LocalDate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface KboGameRepository : JpaRepository<KboGameEntity, Long> {
    fun findByGameID(gameID: String): KboGameEntity?
    fun findByDateOrderByTimeAscGameIDAsc(date: LocalDate): List<KboGameEntity>
    @Query("select distinct k.season from KboGameEntity k")
    fun findDistinctSeasons(): List<Int>
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
