package com.victoryfairy.server.kbo

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
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
        "spring.datasource.url=jdbc:h2:mem:kbo-production-source-labels;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "victory-fairy.kbo.source-label-mode=production",
        "victory-fairy.kbo.scraped-dev.enabled=true",
        "victory-fairy.kbo.scraped-dev.admin-import-token=test-admin-token",
    ],
)
class KboProductionSourceLabelIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var repository: KboGameRepository

    @BeforeEach
    fun resetData() {
        repository.deleteAll()
    }

    @Test
    fun `production source mode returns safe KBO game and standings wording`() {
        mockMvc.post("/api/v1/dev/kbo/seed-sample-game") {
            header("X-Admin-Token", TEST_ADMIN_TOKEN)
        }
            .andExpect { status { isOk() } }

        val gamesResponse = mockMvc.get("/api/v1/kbo/games") {
            param("date", "2026-04-16")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.source") { value("reference") } }
            .andExpect { jsonPath("$.data.sourceLabel") { value(SCRAPED_DEV_REVIEW_SOURCE_LABEL) } }
            .andExpect { jsonPath("$.data.sourceDisclosure") { value(SCRAPED_DEV_SOURCE_DISCLOSURE) } }
            .andExpect { jsonPath("$.data.items[0].source") { value("reference") } }
            .andExpect { jsonPath("$.data.items[0].sourceLabel") { value(SCRAPED_DEV_REVIEW_SOURCE_LABEL) } }
            .andExpect { jsonPath("$.data.items[0].sourceDisclosure") { value(SCRAPED_DEV_SOURCE_DISCLOSURE) } }
            .andReturn()
            .response
            .contentAsString

        assertProductionSafeKboWording(gamesResponse)

        val standingsResponse = mockMvc.get("/api/v1/kbo/standings") {
            param("season", "2026")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.source") { value("reference") } }
            .andExpect { jsonPath("$.data.sourceLabel") { value(SCRAPED_DEV_REVIEW_SOURCE_LABEL) } }
            .andExpect { jsonPath("$.data.sourceDisclosure") { value(SCRAPED_DEV_SOURCE_DISCLOSURE) } }
            .andExpect { jsonPath("$.data.updatedAt") { exists() } }
            .andReturn()
            .response
            .contentAsString

        assertProductionSafeKboWording(standingsResponse)
    }

    @Test
    fun `production empty standings use safe message and nullable freshness`() {
        val response = mockMvc.get("/api/v1/kbo/standings") {
            param("season", "2026")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.source") { value("reference") } }
            .andExpect { jsonPath("$.data.sourceLabel") { value(SCRAPED_DEV_REVIEW_SOURCE_LABEL) } }
            .andExpect { jsonPath("$.data.sourceDisclosure") { value(SCRAPED_DEV_SOURCE_DISCLOSURE) } }
            .andExpect { jsonPath("$.data.updatedAt") { value(null) } }
            .andExpect { jsonPath("$.data.message") { value("수집된 경기 결과가 아직 없습니다.") } }
            .andExpect { jsonPath("$.data.message") { value(not(containsString("공식"))) } }
            .andReturn()
            .response
            .contentAsString

        assertProductionSafeKboWording(response)
    }

    private fun assertProductionSafeKboWording(response: String) {
        assertTrue(response.contains(SCRAPED_DEV_SOURCE_DISCLOSURE))
        assertFalse(response.contains(SCRAPED_DEV_SOURCE_LABEL))
        assertFalse(response.contains(SCRAPED_DEV_SOURCE))
        assertFalse(response.contains("공식 KBO 데이터"))
        assertFalse(response.contains("공식 기록 제공"))
    }

    companion object {
        private const val TEST_ADMIN_TOKEN = "test-admin-token"
    }
}
