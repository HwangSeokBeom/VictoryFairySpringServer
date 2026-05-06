package com.victoryfairy.server.kbo

import com.fasterxml.jackson.databind.ObjectMapper
import com.victoryfairy.server.kbo.collector.KboSchedulePageClient
import com.victoryfairy.server.kbo.collector.KboSeriesType
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
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
        "spring.datasource.url=jdbc:h2:mem:kbo-collector;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "victory-fairy.kbo.scraped-dev.input-json=build/tmp/test-kbo-import.json",
        "victory-fairy.kbo.scraped-dev.state-path=build/tmp/test-kbo-state.json",
    ],
)
class KboCollectorIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var repository: KboGameRepository
    @MockBean lateinit var pageClient: KboSchedulePageClient

    @BeforeEach
    fun resetData() {
        repository.deleteAll()
        Files.deleteIfExists(Path.of("build/tmp/test-kbo-state.json"))
    }

    @Test
    fun `collect endpoint upserts internally and preserves favorite team API contract`() {
        stubSchedulePages()

        mockMvc.post("/api/v1/dev/kbo/collect-scraped-dev") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"season":2026,"seriesType":"REGULAR_SEASON"}"""
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.collectedCount") { value(1) } }
            .andExpect { jsonPath("$.data.inserted") { value(1) } }
            .andExpect { jsonPath("$.data.updated") { value(0) } }
            .andExpect { jsonPath("$.data.statusCounts.final") { value(1) } }

        mockMvc.post("/api/v1/dev/kbo/collect-scraped-dev") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"season":2026,"seriesType":"REGULAR_SEASON"}"""
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.inserted") { value(0) } }
            .andExpect { jsonPath("$.data.updated") { value(1) } }

        mockMvc.get("/api/v1/kbo/games") {
            param("date", "2026-04-16")
            param("teamID", "hanwha-eagles")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.source") { value("scraped-dev") } }
            .andExpect { jsonPath("$.data.sourceLabel") { value("개발용 외부 수집 데이터") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.matchupText") { value("한화 vs 삼성") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.scoreText") { value("1:6 패") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.result") { value("loss") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.ourScore") { value(1) } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.opponentScore") { value(6) } }

        mockMvc.get("/api/v1/kbo/games") {
            param("date", "2026-04-16")
            param("teamID", "samsung-lions")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.matchupText") { value("삼성 vs 한화") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.scoreText") { value("6:1 승") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.result") { value("win") } }
    }

    @Test
    fun `update endpoint defaults to internal collector and json import remains fallback`() {
        stubSchedulePages()

        mockMvc.post("/api/v1/dev/kbo/update-scraped-dev") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.collectedCount") { value(1) } }

        repository.deleteAll()
        Files.createDirectories(Path.of("build/tmp"))
        Files.writeString(
            Path.of("build/tmp/test-kbo-import.json"),
            """
            [
              {"date":"2026-05-01","homeTeam":"LG","awayTeam":"두산","stadium":"잠실","gameStatus":"경기전"}
            ]
            """.trimIndent(),
        )

        mockMvc.post("/api/v1/dev/kbo/update-scraped-dev") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"mode":"json-import"}"""
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.collectedCount") { value(1) } }
            .andExpect { jsonPath("$.data.inserted") { value(1) } }
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
}
