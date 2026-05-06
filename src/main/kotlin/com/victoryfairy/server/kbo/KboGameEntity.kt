package com.victoryfairy.server.kbo

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(
    name = "kbo_games",
    indexes = [
        Index(name = "idx_kbo_games_date", columnList = "date"),
        Index(name = "idx_kbo_games_season", columnList = "season"),
        Index(name = "idx_kbo_games_home_team", columnList = "homeTeamID"),
        Index(name = "idx_kbo_games_away_team", columnList = "awayTeamID"),
        Index(name = "idx_kbo_games_source", columnList = "source"),
    ],
)
class KboGameEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, unique = true, length = 80)
    var gameID: String = "",
    @Column(nullable = false)
    var date: LocalDate = LocalDate.now(),
    @Column(nullable = false)
    var season: Int = 0,
    @Column(length = 40)
    var seriesType: String? = null,
    var time: LocalTime? = null,
    @Column(nullable = false, length = 60)
    var homeTeamID: String = "",
    @Column(nullable = false, length = 60)
    var awayTeamID: String = "",
    @Column(nullable = false, length = 80)
    var homeTeamName: String = "",
    @Column(nullable = false, length = 80)
    var awayTeamName: String = "",
    var homeScore: Int? = null,
    var awayScore: Int? = null,
    @Column(nullable = false, length = 100)
    var stadiumName: String = "",
    @Column(nullable = false, length = 20)
    var status: String = "scheduled",
    @Column(length = 60)
    var winnerTeamID: String? = null,
    @Column(length = 200)
    var resultSummary: String? = null,
    @Column(columnDefinition = "TEXT")
    var highlightTagsJson: String = "[]",
    @Column(nullable = false, length = 30)
    var source: String = "scraped-dev",
    @Column(nullable = false, length = 80)
    var sourceLabel: String = SCRAPED_DEV_SOURCE_LABEL,
    @Column(length = 500)
    var kboGameCenterURL: String? = null,
    @Column(length = 500)
    var kboRecordURL: String? = null,
    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    @PrePersist
    fun prePersist() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }
}

const val SCRAPED_DEV_SOURCE = "scraped-dev"
const val SCRAPED_DEV_SOURCE_LABEL = "개발용 외부 수집 데이터"
