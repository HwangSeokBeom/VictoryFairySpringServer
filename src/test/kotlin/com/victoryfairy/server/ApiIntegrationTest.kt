package com.victoryfairy.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.victoryfairy.server.attendance.AttendanceLogRequest
import kotlin.test.assertTrue
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:api-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class ApiIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var handlerMapping: RequestMappingHandlerMapping

    @Test
    fun `expected mobile API routes are registered`() {
        val mappings = handlerMapping.handlerMethods.keys.flatMap { info ->
            val methods = info.methodsCondition.methods.map { it.name }.ifEmpty { listOf("ANY") }
            val patterns = info.pathPatternsCondition?.patternValues ?: info.patternsCondition?.patterns ?: emptySet()
            patterns.flatMap { pattern -> methods.map { method -> "$method $pattern" } }
        }.toSet()

        listOf(
            "GET /health",
            "GET /api/v1/teams",
            "GET /api/v1/me/preferences",
            "PUT /api/v1/me/preferences",
            "GET /api/v1/feed",
            "GET /api/v1/calendar",
            "GET /api/v1/statistics/summary",
            "GET /api/v1/statistics/stadiums",
            "GET /api/v1/statistics/opponents",
            "GET /api/v1/kbo/standings",
            "GET /api/v1/kbo/games",
            "POST /api/v1/attendance-logs",
            "POST /api/v1/ai/diary-draft",
            "POST /api/v1/ticket/parse-ocr-text",
            "GET /api/v1/seasons",
        ).forEach { expected ->
            assertTrue(expected in mappings, "Missing registered request mapping: $expected")
        }
    }

    @Test
    fun `health returns envelope`() {
        mockMvc.get("/health")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data.status") { value("ok") } }
    }

    @Test
    fun `teams returns ten KBO teams`() {
        mockMvc.get("/api/v1/teams")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data", hasSize<Any>(10)) }
    }

    @Test
    fun `device owned mobile read endpoints return ok with device header`() {
        val deviceID = "local-test-device"

        mockMvc.get("/api/v1/me/preferences") {
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }

        mockMvc.get("/api/v1/feed") {
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }

        mockMvc.get("/api/v1/calendar") {
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }

        mockMvc.get("/api/v1/statistics/summary") {
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
    }

    @Test
    fun `KBO standings route returns ok for configured season`() {
        mockMvc.get("/api/v1/kbo/standings") {
            param("season", "2026")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
    }

    @Test
    fun `seasons returns configured and available seasons`() {
        val deviceID = "season-test-device"
        mockMvc.post("/api/v1/attendance-logs") {
            header("X-Device-ID", deviceID)
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                AttendanceLogRequest(
                    date = "2025-04-01",
                    season = 2025,
                    favoriteTeamID = "hanwha-eagles",
                    opponentTeamID = "lg-twins",
                    stadiumName = "대전 한화생명 볼파크",
                    result = "win",
                    ourScore = 7,
                    opponentScore = 4,
                ),
            )
        }
            .andExpect { status { isOk() } }

        mockMvc.get("/api/v1/seasons") {
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data.currentSeason") { value(2026) } }
            .andExpect { jsonPath("$.data.items[0].season") { value(2026) } }
            .andExpect { jsonPath("$.data.items[0].label") { value("2026 시즌") } }
            .andExpect { jsonPath("$.data.items[1].season") { value(2025) } }
            .andExpect { jsonPath("$.data.items[1].hasRecords") { value(true) } }
    }

    @Test
    fun `sample KBO game returns favorite team perspective for both teams`() {
        mockMvc.post("/api/v1/dev/kbo/seed-sample-game")
            .andExpect { status { isOk() } }

        mockMvc.get("/api/v1/kbo/games") {
            param("date", "2026-04-16")
            param("teamID", "hanwha-eagles")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.source") { value("scraped-dev") } }
            .andExpect { jsonPath("$.data.sourceLabel") { value("개발용 외부 수집 데이터") } }
            .andExpect { jsonPath("$.data.items[0].homeTeamID") { value("hanwha-eagles") } }
            .andExpect { jsonPath("$.data.items[0].awayTeamID") { value("samsung-lions") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.result") { value("loss") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.ourScore") { value(1) } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.opponentScore") { value(6) } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.scoreText") { value("1:6 패") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.matchupText") { value("한화 vs 삼성") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.shortMemo") { value("삼성이 6:1로 승리했던 경기") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.diaryTemplate") { value("오늘은 대전 한화생명 볼파크에서 한화 이글스와 삼성 라이온즈의 경기를 직관했다. 결과는 1:6 패배였지만, 경기장의 분위기와 응원은 오래 기억에 남았다.") } }

        mockMvc.get("/api/v1/kbo/games") {
            param("date", "2026-04-16")
            param("teamID", "samsung-lions")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.result") { value("win") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.ourScore") { value(6) } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.opponentScore") { value(1) } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.scoreText") { value("6:1 승") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.matchupText") { value("삼성 vs 한화") } }
            .andExpect { jsonPath("$.data.items[0].attendanceSuggestion.shortMemo") { value("삼성이 6:1로 승리했던 경기") } }
    }

    @Test
    fun `AI disabled endpoint returns AI_FEATURE_DISABLED failure envelope`() {
        mockMvc.post("/api/v1/ai/diary-draft") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(false) } }
            .andExpect { jsonPath("$.error.code") { value("AI_FEATURE_DISABLED") } }
            .andExpect { jsonPath("$.error.message") { value("AI 후기 초안 기능은 비활성화되어 있습니다.") } }
    }

    @Test
    fun `scheduler is disabled by default`() {
        mockMvc.get("/api/v1/dev/kbo/update-scraped-dev/status")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.enabled") { value(false) } }
            .andExpect { jsonPath("$.data.source") { value("scraped-dev") } }
    }

    @Test
    fun `statistics winRate excludes draw and canceled`() {
        val deviceID = "00000000-0000-4000-8000-000000000001"
        listOf(
            AttendanceLogRequest(date = "2026-04-01", season = 2026, favoriteTeamID = "hanwha-eagles", opponentTeamID = "lg-twins", stadiumName = "대전 한화생명 볼파크", result = "win", ourScore = 7, opponentScore = 4),
            AttendanceLogRequest(date = "2026-04-02", season = 2026, favoriteTeamID = "hanwha-eagles", opponentTeamID = "samsung-lions", stadiumName = "대전 한화생명 볼파크", result = "loss", ourScore = 1, opponentScore = 6),
            AttendanceLogRequest(date = "2026-04-03", season = 2026, favoriteTeamID = "hanwha-eagles", opponentTeamID = "nc-dinos", stadiumName = "대전 한화생명 볼파크", result = "draw", ourScore = 5, opponentScore = 5),
            AttendanceLogRequest(date = "2026-04-04", season = 2026, favoriteTeamID = "hanwha-eagles", opponentTeamID = "kia-tigers", stadiumName = "대전 한화생명 볼파크", result = "canceled"),
        ).forEach {
            mockMvc.post("/api/v1/attendance-logs") {
                header("X-Device-ID", deviceID)
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(it)
            }.andExpect { status { isOk() } }
        }

        mockMvc.get("/api/v1/statistics/summary") {
            header("X-Device-ID", deviceID)
            param("season", "2026")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.totalGames") { value(4) } }
            .andExpect { jsonPath("$.data.winRate") { value(0.5) } }
    }
}
