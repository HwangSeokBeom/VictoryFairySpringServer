package com.victoryfairy.server.kbo

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:kbo-standings;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class KboStandingsIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var repository: KboGameRepository
    @Autowired lateinit var gameService: KboGameService
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun resetData() {
        repository.deleteAll()
    }

    @Test
    fun `seeded sample game ranks Samsung above Hanwha`() {
        mockMvc.post("/api/v1/dev/kbo/seed-sample-game")
            .andExpect { status { isOk() } }

        mockMvc.get("/api/v1/kbo/standings") {
            param("season", "2026")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.source") { value("scraped-dev") } }
            .andExpect { jsonPath("$.data.sourceLabel") { value("개발용 외부 수집 데이터") } }
            .andExpect { jsonPath("$.data.updatedAt") { exists() } }
            .andExpect { jsonPath("$.data.items", hasSize<Any>(2)) }
            .andExpect { jsonPath("$.data.items[0].rank") { value(1) } }
            .andExpect { jsonPath("$.data.items[0].teamID") { value("samsung-lions") } }
            .andExpect { jsonPath("$.data.items[0].wins") { value(1) } }
            .andExpect { jsonPath("$.data.items[0].runDifferential") { value(5) } }
            .andExpect { jsonPath("$.data.items[0].recentResults[0]") { value("W") } }
            .andExpect { jsonPath("$.data.items[1].teamID") { value("hanwha-eagles") } }
            .andExpect { jsonPath("$.data.items[1].losses") { value(1) } }
            .andExpect { jsonPath("$.data.items[1].recentResults[0]") { value("L") } }
            .andExpect { jsonPath("$.data.message") { doesNotExist() } }
    }

    @Test
    fun `standings updatedAt is latest final game updatedAt`() {
        upsertGame(
            gameID = "final-lg-doosan",
            date = "2026-04-01",
            homeTeamID = "lg-twins",
            awayTeamID = "doosan-bears",
            homeScore = 3,
            awayScore = 2,
            status = "final",
            updatedAt = Instant.parse("2026-04-01T01:15:00Z"),
        )
        upsertGame(
            gameID = "final-kia-nc",
            date = "2026-04-02",
            homeTeamID = "kia-tigers",
            awayTeamID = "nc-dinos",
            homeScore = 1,
            awayScore = 7,
            status = "final",
            updatedAt = Instant.parse("2026-04-02T09:30:00Z"),
        )
        upsertGame(
            gameID = "scheduled-lotte-ssg",
            date = "2026-04-03",
            homeTeamID = "lotte-giants",
            awayTeamID = "ssg-landers",
            homeScore = null,
            awayScore = null,
            status = "scheduled",
            updatedAt = Instant.parse("2026-04-03T12:00:00Z"),
        )

        mockMvc.get("/api/v1/kbo/standings") {
            param("season", "2026")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.source") { value("scraped-dev") } }
            .andExpect { jsonPath("$.data.sourceLabel") { value("개발용 외부 수집 데이터") } }
            .andExpect { jsonPath("$.data.updatedAt") { value("2026-04-02T18:30:00.000+09:00") } }
            .andExpect { jsonPath("$.data.items", hasSize<Any>(4)) }
    }

    @Test
    fun `scheduled and canceled games are excluded from standings`() {
        upsertGame("final-lg-doosan", "2026-04-01", "lg-twins", "doosan-bears", 3, 2, "final")
        upsertGame("scheduled-kia-nc", "2026-04-02", "kia-tigers", "nc-dinos", null, null, "scheduled")
        upsertGame("canceled-kia-nc", "2026-04-03", "kia-tigers", "nc-dinos", null, null, "canceled")

        mockMvc.get("/api/v1/kbo/standings") {
            param("season", "2026")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.items", hasSize<Any>(2)) }
            .andExpect { jsonPath("$.data.items[0].teamID") { value("lg-twins") } }
            .andExpect { jsonPath("$.data.items[0].games") { value(1) } }
            .andExpect { jsonPath("$.data.items[1].teamID") { value("doosan-bears") } }
            .andExpect { jsonPath("$.data.items[1].games") { value(1) } }
    }

    @Test
    fun `draws are excluded from win rate denominator`() {
        upsertGame("lg-doosan-draw", "2026-04-01", "lg-twins", "doosan-bears", 2, 2, "final")
        upsertGame("lg-nc-win", "2026-04-02", "lg-twins", "nc-dinos", 4, 1, "final")

        mockMvc.get("/api/v1/kbo/standings") {
            param("season", "2026")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.items[0].teamID") { value("lg-twins") } }
            .andExpect { jsonPath("$.data.items[0].games") { value(2) } }
            .andExpect { jsonPath("$.data.items[0].wins") { value(1) } }
            .andExpect { jsonPath("$.data.items[0].draws") { value(1) } }
            .andExpect { jsonPath("$.data.items[0].winRate") { value(1.0) } }
            .andExpect { jsonPath("$.data.items[0].recentResults[0]") { value("W") } }
            .andExpect { jsonPath("$.data.items[0].recentResults[1]") { value("D") } }
    }

    @Test
    fun `empty standings remain scraped-dev and avoid official-data wording`() {
        mockMvc.get("/api/v1/kbo/standings") {
            param("season", "2026")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.source") { value("scraped-dev") } }
            .andExpect { jsonPath("$.data.sourceLabel") { value("개발용 외부 수집 데이터") } }
            .andExpect { jsonPath("$.data.updatedAt") { value(null) } }
            .andExpect { jsonPath("$.data.items", hasSize<Any>(0)) }
            .andExpect { jsonPath("$.data.message") { value("수집된 경기 결과가 아직 없습니다.") } }
            .andExpect { jsonPath("$.data.message") { value(not(containsString("공식"))) } }
    }

    @Test
    fun `empty standings updatedAt falls back to latest season game update`() {
        upsertGame(
            gameID = "scheduled-lg-doosan",
            date = "2026-04-01",
            homeTeamID = "lg-twins",
            awayTeamID = "doosan-bears",
            homeScore = null,
            awayScore = null,
            status = "scheduled",
            updatedAt = Instant.parse("2026-04-01T04:00:00Z"),
        )
        upsertGame(
            gameID = "canceled-kia-nc",
            date = "2026-04-02",
            homeTeamID = "kia-tigers",
            awayTeamID = "nc-dinos",
            homeScore = null,
            awayScore = null,
            status = "canceled",
            updatedAt = Instant.parse("2026-04-02T05:45:00Z"),
        )

        mockMvc.get("/api/v1/kbo/standings") {
            param("season", "2026")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.source") { value("scraped-dev") } }
            .andExpect { jsonPath("$.data.sourceLabel") { value("개발용 외부 수집 데이터") } }
            .andExpect { jsonPath("$.data.updatedAt") { value("2026-04-02T14:45:00.000+09:00") } }
            .andExpect { jsonPath("$.data.items", hasSize<Any>(0)) }
            .andExpect { jsonPath("$.data.message") { value("수집된 경기 결과가 아직 없습니다.") } }
    }

    private fun upsertGame(
        gameID: String,
        date: String,
        homeTeamID: String,
        awayTeamID: String,
        homeScore: Int?,
        awayScore: Int?,
        status: String,
        updatedAt: Instant? = null,
    ) {
        val result = gameService.upsert(
            NormalizedKboGame(
                gameID = gameID,
                date = LocalDate.parse(date),
                season = 2026,
                seriesType = "REGULAR_SEASON",
                time = null,
                homeTeamID = homeTeamID,
                awayTeamID = awayTeamID,
                homeScore = homeScore,
                awayScore = awayScore,
                stadiumName = "테스트 구장",
                status = status,
                kboGameCenterURL = null,
                kboRecordURL = null,
                highlightTags = emptyList(),
            ),
        )
        if (updatedAt != null) {
            jdbcTemplate.update(
                "update kbo_games set updated_at = ? where id = ?",
                Timestamp.from(updatedAt),
                result.entity.id,
            )
        }
    }
}
