package com.victoryfairy.server.kbo

import com.victoryfairy.server.kbo.collector.KboSchedulePageClient
import com.victoryfairy.server.kbo.collector.KboSeriesType
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
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("production")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:kbo-refresh-production;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "victory-fairy.kbo.refresh.admin-token=test-refresh-token",
        "victory-fairy.kbo.scraped-dev.enabled=false",
    ],
)
class KboRefreshProductionIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var repository: KboGameRepository
    @MockBean lateinit var pageClient: KboSchedulePageClient

    @BeforeEach
    fun resetData() {
        repository.deleteAll()
        reset(pageClient)
    }

    @Test
    fun `production profile blocks collect scraped dev endpoint`() {
        mockMvc.post("/api/v1/dev/kbo/collect-scraped-dev") {
            header("X-Admin-Token", TEST_REFRESH_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"season":2026,"seriesType":"REGULAR_SEASON"}"""
        }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `production profile allows admin KBO refresh with valid token`() {
        stubSchedulePages()

        mockMvc.post("/api/v1/admin/kbo/refresh") {
            header("X-Admin-Token", TEST_REFRESH_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"season":2026}"""
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

    companion object {
        private const val TEST_REFRESH_TOKEN = "test-refresh-token"
    }
}
