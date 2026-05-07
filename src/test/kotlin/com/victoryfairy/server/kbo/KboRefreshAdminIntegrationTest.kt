package com.victoryfairy.server.kbo

import com.victoryfairy.server.kbo.collector.KboSchedulePageClient
import com.victoryfairy.server.kbo.collector.KboSeriesType
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:kbo-refresh-admin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "victory-fairy.kbo.source-label-mode=production",
        "victory-fairy.kbo.refresh.admin-token=test-refresh-token",
        "victory-fairy.kbo.scraped-dev.enabled=false",
    ],
)
class KboRefreshAdminIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var repository: KboGameRepository
    @Autowired lateinit var gameService: KboGameService
    @MockBean lateinit var pageClient: KboSchedulePageClient

    @BeforeEach
    fun resetData() {
        repository.deleteAll()
        reset(pageClient)
    }

    @Test
    fun `admin refresh endpoint returns 403 without token`() {
        mockMvc.post("/api/v1/admin/kbo/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"season":2026}"""
        }
            .andExpect { status { isForbidden() } }
            .andExpect { jsonPath("$.error.code") { value("ADMIN_TOKEN_REQUIRED") } }
    }

    @Test
    fun `admin refresh endpoint returns 403 with wrong token`() {
        mockMvc.post("/api/v1/admin/kbo/refresh") {
            header("X-Admin-Token", "wrong")
            contentType = MediaType.APPLICATION_JSON
            content = """{"season":2026}"""
        }
            .andExpect { status { isForbidden() } }
            .andExpect { jsonPath("$.error.code") { value("ADMIN_TOKEN_INVALID") } }
    }

    @Test
    fun `admin refresh endpoint returns 200 with valid token`() {
        stubSchedulePages()

        mockMvc.post("/api/v1/admin/kbo/refresh") {
            header("X-Admin-Token", TEST_REFRESH_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"season":2026}"""
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.season") { value(2026) } }
            .andExpect { jsonPath("$.data.collectedCount") { value(1) } }
            .andExpect { jsonPath("$.data.inserted") { value(1) } }
            .andExpect { jsonPath("$.data.updated") { value(0) } }
            .andExpect { jsonPath("$.data.statusCounts.final") { value(1) } }
            .andExpect { jsonPath("$.data.startedAt") { exists() } }
            .andExpect { jsonPath("$.data.finishedAt") { exists() } }
    }

    @Test
    fun `refresh failure does not delete existing KboGame rows`() {
        seedFinalGame()
        assertEquals(1, repository.count())
        for (month in 1..12) {
            given(pageClient.fetchScheduleTableHtml(2026, month, KboSeriesType.REGULAR_SEASON))
                .willThrow(RuntimeException("Playwright browser unavailable"))
        }

        mockMvc.post("/api/v1/admin/kbo/refresh") {
            header("X-Admin-Token", TEST_REFRESH_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"season":2026}"""
        }
            .andExpect { status { isServiceUnavailable() } }
            .andExpect { jsonPath("$.error.code") { value("KBO_REFRESH_FAILED") } }

        assertEquals(1, repository.count())
    }

    @Test
    fun `standings still return safe sourceLabel and sourceDisclosure`() {
        seedFinalGame()

        val response = mockMvc.get("/api/v1/kbo/standings") {
            param("season", "2026")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.source") { value("reference") } }
            .andExpect { jsonPath("$.data.sourceLabel") { value(SCRAPED_DEV_REVIEW_SOURCE_LABEL) } }
            .andExpect { jsonPath("$.data.sourceDisclosure") { value(SCRAPED_DEV_SOURCE_DISCLOSURE) } }
            .andReturn()
            .response
            .contentAsString

        kotlin.test.assertFalse(response.contains(SCRAPED_DEV_SOURCE_LABEL))
        kotlin.test.assertFalse(response.contains(SCRAPED_DEV_SOURCE))
        kotlin.test.assertFalse(response.contains("공식 KBO 데이터"))
        kotlin.test.assertFalse(response.contains("공식 기록 제공"))
    }

    @Test
    fun `public standings and games endpoints do not require admin token`() {
        seedFinalGame()

        mockMvc.get("/api/v1/kbo/standings") {
            param("season", "2026")
        }
            .andExpect { status { isOk() } }

        mockMvc.get("/api/v1/kbo/games") {
            param("date", "2026-04-16")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.sourceLabel") { value(SCRAPED_DEV_REVIEW_SOURCE_LABEL) } }
            .andExpect { jsonPath("$.data.message") { doesNotExist() } }
    }

    private fun seedFinalGame() {
        gameService.upsert(
            NormalizedKboGame(
                gameID = "2026-04-16-hanwha-samsung-refresh-test",
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
            ),
        )
    }

    private fun stubSchedulePages() {
        for (month in 1..12) {
            given(pageClient.fetchScheduleTableHtml(2026, month, KboSeriesType.REGULAR_SEASON)).willReturn(noGamesHtml())
        }
        given(pageClient.fetchScheduleTableHtml(2026, 4, KboSeriesType.REGULAR_SEASON)).willReturn(hanwhaLossHtml())
    }

    private fun noGamesHtml(): String = """<table id="tblScheduleList"><tbody><tr><td colspan="9">데이터가 없습니다.</td></tr></tbody></table>"""

    private fun hanwhaLossHtml(): String = """
        <table id="tblScheduleList"><tbody>
          <tr>
            <td class="day">04.16(목)</td>
            <td class="time"><b>18:30</b></td>
            <td class="play"><span>삼성</span><em><span class="win">6</span><span>vs</span><span class="lose">1</span></em><span>한화</span></td>
            <td class="relay"><a href="https://www.koreabaseball.com/Schedule/GameCenter/Main.aspx?gameDate=20260416&amp;gameId=20260416SSHH0&amp;section=REVIEW">리뷰</a></td>
            <td></td><td>SPO-T</td><td></td><td>대전</td><td>-</td>
          </tr>
        </tbody></table>
    """.trimIndent()

    companion object {
        private const val TEST_REFRESH_TOKEN = "test-refresh-token"
    }
}
