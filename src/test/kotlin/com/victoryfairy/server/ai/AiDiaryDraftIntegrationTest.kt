package com.victoryfairy.server.ai

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Duration

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:ai-diary;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "victory-fairy.ai.diary-enabled=true",
        "victory-fairy.ai.groq-api-key=test-only-secret",
        "victory-fairy.ai.daily-limit=1",
    ],
)
class AiDiaryDraftIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var groqClient: FakeGroqClient

    @BeforeEach
    fun resetFakeClient() {
        groqClient.draftResult = null
        groqClient.repairResult = null
        groqClient.requestCount = 0
    }

    @Test
    fun `Groq client mocked success returns valid draft and does not expose API key`() {
        groqClient.draftResult =
            GroqDraftResult(
                """{"draftText":"대전 한화생명 볼파크에서 남긴 따뜻한 직관 초안입니다.","summaryText":"한화 1:6 패 직관","shareText":"대전에서 남긴 KBO 직관 기록","hashtags":["#승리요정","#KBO직관","#대전한화생명볼파크"]}""",
                totalTokens = 120,
            )

        val response = mockMvc.post("/api/v1/ai/diary-draft") {
            header("X-Device-ID", "ai-success-device")
            contentType = MediaType.APPLICATION_JSON
            content = validPayload()
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data.source") { value("groq") } }
            .andExpect { jsonPath("$.data.model") { value("llama-3.1-8b-instant") } }
            .andExpect { jsonPath("$.data.draftText") { value("대전 한화생명 볼파크에서 남긴 따뜻한 직관 초안입니다.") } }
            .andExpect { jsonPath("$.data.hashtags[0]") { value("#승리요정") } }
            .andExpect { jsonPath("$", not(containsString("test-only-secret"))) }
            .andReturn()

        kotlin.test.assertFalse(response.response.contentAsString.contains("test-only-secret"))
        kotlin.test.assertEquals(1, groqClient.requestCount)
    }

    @Test
    fun `Groq invalid JSON returns AI_DRAFT_INVALID_RESPONSE`() {
        groqClient.draftResult = GroqDraftResult("not json")
        groqClient.repairResult = GroqDraftResult("still not json")

        mockMvc.post("/api/v1/ai/diary-draft") {
            header("X-Device-ID", "ai-invalid-json-device")
            contentType = MediaType.APPLICATION_JSON
            content = validPayload()
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(false) } }
            .andExpect { jsonPath("$.error.code") { value("AI_DRAFT_INVALID_RESPONSE") } }
    }

    @Test
    fun `rate limit returns AI_DAILY_LIMIT_EXCEEDED`() {
        groqClient.draftResult =
            GroqDraftResult("""{"draftText":"초안입니다.","summaryText":"요약","shareText":"공유","hashtags":["#승리요정"]}""")

        repeat(2) { index ->
            mockMvc.post("/api/v1/ai/diary-draft") {
                header("X-Device-ID", "ai-rate-limited-device")
                contentType = MediaType.APPLICATION_JSON
                content = validPayload()
            }
                .andExpect { status { isOk() } }
                .andExpect {
                    if (index == 0) jsonPath("$.success") { value(true) } else jsonPath("$.error.code") { value("AI_DAILY_LIMIT_EXCEEDED") }
                }
        }
    }

    private fun validPayload(): String =
        """
        {
          "gameDate": "2026-04-16",
          "favoriteTeamName": "한화 이글스",
          "opponentTeamName": "삼성 라이온즈",
          "stadiumName": "대전 한화생명 볼파크",
          "result": "loss",
          "scoreText": "1:6 패",
          "moodTags": ["아쉬움", "열광적"],
          "highlightTags": ["응원 분위기"],
          "companionType": "friends",
          "tone": "warm",
          "extraNoteSanitized": "응원 분위기가 기억에 남았다.",
          "locale": "ko-KR"
        }
        """.trimIndent()

    @TestConfiguration
    class FakeGroqConfig {
        @Bean
        @Primary
        fun fakeGroqClient(): FakeGroqClient = FakeGroqClient()
    }

    class FakeGroqClient : GroqClient() {
        var draftResult: GroqDraftResult? = null
        var repairResult: GroqDraftResult? = null
        var requestCount: Int = 0

        override fun requestDraft(apiKey: String, model: String, prompt: String, timeout: Duration): GroqDraftResult {
            requestCount += 1
            kotlin.test.assertEquals("test-only-secret", apiKey)
            kotlin.test.assertEquals(Duration.ofSeconds(12), timeout)
            return draftResult ?: error("draftResult was not configured")
        }

        override fun requestRepair(apiKey: String, model: String, invalidContent: String, timeout: Duration): GroqDraftResult {
            return repairResult ?: error("repairResult was not configured")
        }
    }
}
