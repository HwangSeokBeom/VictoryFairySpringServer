package com.victoryfairy.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.victoryfairy.server.attendance.AttendanceLogRequest
import kotlin.test.assertFalse
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
            "GET /api/v1/analysis/win-rate",
            "GET /api/v1/news",
            "POST /api/v1/match-outlook",
            "GET /api/v1/community/posts",
            "POST /api/v1/community/posts",
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

    @Test
    fun `win-rate analysis summarizes attendance logs and rankings safely`() {
        val deviceID = "analysis-test-device"
        listOf(
            AttendanceLogRequest(date = "2026-04-01", season = 2026, favoriteTeamID = "samsung-lions", opponentTeamID = "kia-tigers", stadiumName = "잠실야구장", result = "loss", ourScore = 1, opponentScore = 4),
            AttendanceLogRequest(date = "2026-04-02", season = 2026, favoriteTeamID = "samsung-lions", opponentTeamID = "lg-twins", stadiumName = "잠실야구장", result = "win", ourScore = 5, opponentScore = 2),
            AttendanceLogRequest(date = "2026-04-03", season = 2026, favoriteTeamID = "samsung-lions", opponentTeamID = "lg-twins", stadiumName = "대구 삼성 라이온즈 파크", result = "draw", ourScore = 3, opponentScore = 3),
            AttendanceLogRequest(date = "2026-04-04", season = 2026, favoriteTeamID = "samsung-lions", opponentTeamID = "nc-dinos", stadiumName = "대구 삼성 라이온즈 파크", result = "canceled"),
        ).forEach { createAttendance(deviceID, it) }

        mockMvc.get("/api/v1/analysis/win-rate") {
            header("X-Device-ID", deviceID)
            param("season", "2026")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data.season") { value(2026) } }
            .andExpect { jsonPath("$.data.summary.totalGames") { value(4) } }
            .andExpect { jsonPath("$.data.summary.wins") { value(1) } }
            .andExpect { jsonPath("$.data.summary.losses") { value(1) } }
            .andExpect { jsonPath("$.data.summary.draws") { value(1) } }
            .andExpect { jsonPath("$.data.summary.canceled") { value(1) } }
            .andExpect { jsonPath("$.data.summary.winRate") { value(0.5) } }
            .andExpect { jsonPath("$.data.summary.sampleWarning") { value("아직 표본이 적어 재미용으로만 봐주세요.") } }
            .andExpect { jsonPath("$.data.opponentRankings[0].teamID") { value("lg-twins") } }
            .andExpect { jsonPath("$.data.opponentRankings[0].teamName") { value("LG 트윈스") } }
            .andExpect { jsonPath("$.data.opponentRankings[0].games") { value(2) } }
            .andExpect { jsonPath("$.data.opponentRankings[0].winRate") { value(1.0) } }
            .andExpect { jsonPath("$.data.stadiumRankings[0].stadiumName") { value("잠실야구장") } }
            .andExpect { jsonPath("$.data.stadiumRankings[0].games") { value(2) } }
            .andExpect { jsonPath("$.data.stadiumRankings[0].winRate") { value(0.5) } }
            .andExpect { jsonPath("$.data.recentTrend[0]") { value("C") } }
    }

    @Test
    fun `news endpoint returns link-out disclosure without official claim`() {
        val result = mockMvc.get("/api/v1/news") {
            param("teamID", "samsung-lions")
            param("limit", "20")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data.items", hasSize<Any>(1)) }
            .andExpect { jsonPath("$.data.message") { value("개발용 샘플 뉴스입니다.") } }
            .andExpect { jsonPath("$.data.sourceDisclosure") { value("뉴스는 외부 매체로 이동해 확인해 주세요.") } }
            .andReturn()

        assertFalse(result.response.contentAsString.contains("공식"))
        assertFalse(result.response.contentAsString.contains("\"body\""))
    }

    @Test
    fun `match outlook returns safe viewing points from attendance history`() {
        val deviceID = "match-outlook-test-device"
        listOf(
            AttendanceLogRequest(date = "2026-04-01", season = 2026, favoriteTeamID = "samsung-lions", opponentTeamID = "kia-tigers", stadiumName = "잠실야구장", result = "win", ourScore = 5, opponentScore = 2),
            AttendanceLogRequest(date = "2026-04-02", season = 2026, favoriteTeamID = "samsung-lions", opponentTeamID = "kia-tigers", stadiumName = "잠실야구장", result = "loss", ourScore = 1, opponentScore = 4),
            AttendanceLogRequest(date = "2026-04-03", season = 2026, favoriteTeamID = "samsung-lions", opponentTeamID = "kia-tigers", stadiumName = "잠실야구장", result = "win", ourScore = 3, opponentScore = 1),
        ).forEach { createAttendance(deviceID, it) }

        val result = mockMvc.post("/api/v1/match-outlook") {
            header("X-Device-ID", deviceID)
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "favoriteTeamID": "samsung-lions",
                  "opponentTeamID": "kia-tigers",
                  "date": "2026-04-12",
                  "stadiumName": "잠실야구장"
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data.title") { value("오늘의 관전 포인트") } }
            .andExpect { jsonPath("$.data.summary") { value("내 직관 기록과 참고용 경기 정보를 바탕으로 본 응원 포인트예요.") } }
            .andExpect { jsonPath("$.data.points[0]") { value("KIA전 직관 기록은 2승 1패 흐름이에요.") } }
            .andExpect { jsonPath("$.data.confidenceLabel") { value("재미용") } }
            .andExpect { jsonPath("$.data.disclaimer") { value("공식 예측이나 베팅 정보가 아닙니다.") } }
            .andReturn()

        val body = result.response.contentAsString
        listOf(
            "\ubc30\ub2f9",
            "\ub3c4\ubc15",
            "\uc801\uc911\ub960",
            "od" + "ds",
            "money" + "line",
            "spr" + "ead",
            "over" + "-" + "under",
            "wa" + "ger",
        ).forEach {
            assertFalse(body.contains(it, ignoreCase = true), "Unsafe outlook term present: $it")
        }
    }

    @Test
    fun `match outlook handles insufficient attendance samples`() {
        val deviceID = "match-outlook-small-sample-device"

        mockMvc.post("/api/v1/match-outlook") {
            header("X-Device-ID", deviceID)
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "favoriteTeamID": "samsung-lions",
                  "opponentTeamID": "kia-tigers",
                  "date": "2026-04-12",
                  "stadiumName": "잠실야구장"
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.points[0]") { value("최근 직관 기록 기준으로는 KIA전 표본이 아직 적어요.") } }
            .andExpect { jsonPath("$.data.points[1]") { value("잠실야구장 기록을 더 쌓으면 구장별 흐름을 더 잘 볼 수 있어요.") } }
    }

    @Test
    fun `community scaffold returns empty state and disables posting by default`() {
        mockMvc.get("/api/v1/community/posts")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data.items", hasSize<Any>(0)) }
            .andExpect { jsonPath("$.data.message") { value("응원톡은 준비 중입니다.") } }
            .andExpect { jsonPath("$.data.policyURL") { value("https://hwangseokbeom.github.io/VictoryFairy-legal/community-policy.html") } }

        mockMvc.post("/api/v1/community/posts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"삼성 화이팅"}"""
        }
            .andExpect { status { isForbidden() } }
            .andExpect { jsonPath("$.success") { value(false) } }
            .andExpect { jsonPath("$.error.code") { value("COMMUNITY_DISABLED") } }
    }

    private fun createAttendance(deviceID: String, request: AttendanceLogRequest) {
        mockMvc.post("/api/v1/attendance-logs") {
            header("X-Device-ID", deviceID)
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect { status { isOk() } }
    }
}
