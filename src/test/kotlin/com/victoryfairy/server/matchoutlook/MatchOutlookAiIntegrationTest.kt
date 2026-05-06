package com.victoryfairy.server.matchoutlook

import com.fasterxml.jackson.databind.ObjectMapper
import com.victoryfairy.server.ai.GroqClient
import com.victoryfairy.server.ai.GroqDraftResult
import com.victoryfairy.server.news.NaverNewsClient
import com.victoryfairy.server.news.NaverNewsItem
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:match-outlook-ai;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "victory-fairy.ai.match-outlook-enabled=true",
        "victory-fairy.ai.groq-api-key=test-groq-key",
        "victory-fairy.news.provider=naver",
        "victory-fairy.news.naver-client-id=test-naver-client-id",
        "victory-fairy.news.naver-client-secret=test-naver-client-secret",
    ],
)
class MatchOutlookAiIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var groqClient: FakeGroqClient
    @Autowired lateinit var naverNewsClient: FakeNaverNewsClient

    @BeforeEach
    fun resetFakes() {
        groqClient.content = validLgDoosanAiContent()
        naverNewsClient.reset()
    }

    @Test
    fun `mocked AI success returns generatedBy ai and supplied news references`() {
        val result = postOutlook()
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data.generatedBy") { value("ai") } }
            .andExpect { jsonPath("$.data.points", hasSize<Any>(3)) }
            .andExpect { jsonPath("$.data.points[0].title") { value("최근 분위기") } }
            .andExpect { jsonPath("$.data.points[0].body") { exists() } }
            .andExpect { jsonPath("$.data.newsReferences", hasSize<Any>(5)) }
            .andExpect { jsonPath("$.data.summary") { value("최근 야구 소식과 내 직관 기록을 바탕으로 오늘 경기를 더 재미있게 볼 포인트를 정리했어요.") } }
            .andReturn()

        assertEquals(
            listOf("LG 트윈스 두산 베어스 야구", "LG 트윈스 야구", "두산 베어스 야구"),
            naverNewsClient.queries,
        )
        val body = result.response.contentAsString
        val urls = objectMapper.readTree(body).path("data").path("newsReferences").map { it.path("url").asText() }.toSet()
        assertTrue(urls.isNotEmpty())
        assertTrue(urls.all { it in FakeNaverNewsClient.SUPPLIED_URLS }, "Response used an unsupplied news URL: $urls")
        assertFalse(body.contains("내 직관 기록과 참고용 경기 정보를 바탕으로 본 응원 포인트예요."))
    }

    @Test
    fun `banned AI terms trigger template fallback`() {
        groqClient.content = """
            {
              "points": [
                {"title": "최근 분위기", "body": "오늘은 승리 확률을 따져 보면 좋아요."},
                {"title": "내 직관 기록", "body": "직관 기록을 함께 확인해요."},
                {"title": "응원 포인트", "body": "응원 흐름을 즐겨보세요."}
              ]
            }
        """.trimIndent()

        val result = postOutlook()
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.generatedBy") { value("template") } }
            .andExpect { jsonPath("$.data.points[0].title") { value("최근 분위기") } }
            .andExpect { jsonPath("$.data.newsReferences", hasSize<Any>(5)) }
            .andReturn()

        assertFalse(result.response.contentAsString.contains("승리 확률"))
    }

    @Test
    fun `LG vs Doosan rejects Samsung and KIA news`() {
        naverNewsClient.itemsByQuery = mutableMapOf(
            "LG 트윈스 두산 베어스 야구" to listOf(news("삼성에 최형우 없었다면 어쩔 뻔", "KIA와 삼성 중심 소식", "https://news.example.com/rejected-samsung")),
            "LG 트윈스 야구" to listOf(news("KIA 타이거즈 연승", "최형우 활약", "https://news.example.com/rejected-kia")),
            "두산 베어스 야구" to listOf(news("한화 이글스 새 소식", "대전 야구 이야기", "https://news.example.com/rejected-hanwha")),
        )

        val result = postOutlook()
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.newsReferences", hasSize<Any>(0)) }
            .andReturn()

        val body = result.response.contentAsString
        listOf("삼성", "KIA", "최형우", "한화").forEach {
            assertFalse(body.contains(it), "Unrelated term present: $it")
        }
    }

    @Test
    fun `LG vs Doosan accepts LG news`() {
        naverNewsClient.itemsByQuery = mutableMapOf(
            "LG 트윈스 야구" to listOf(news("LG 트윈스 잠실 훈련", "엘지 타선 점검", "https://news.example.com/accepted-lg")),
        )

        val body = postOutlook()
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.newsReferences", hasSize<Any>(1)) }
            .andReturn()
            .response.contentAsString

        val urls = objectMapper.readTree(body).path("data").path("newsReferences").map { it.path("url").asText() }
        assertEquals(listOf("https://news.example.com/accepted-lg"), urls)
    }

    @Test
    fun `LG vs Doosan accepts Doosan news`() {
        naverNewsClient.itemsByQuery = mutableMapOf(
            "두산 베어스 야구" to listOf(news("두산 베어스 잠실 준비", "두산 불펜 점검", "https://news.example.com/accepted-doosan")),
        )

        val body = postOutlook()
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.newsReferences", hasSize<Any>(1)) }
            .andReturn()
            .response.contentAsString

        val urls = objectMapper.readTree(body).path("data").path("newsReferences").map { it.path("url").asText() }
        assertEquals(listOf("https://news.example.com/accepted-doosan"), urls)
    }

    @Test
    fun `LG vs Doosan accepts article mentioning both teams`() {
        naverNewsClient.itemsByQuery = mutableMapOf(
            "LG 트윈스 두산 베어스 야구" to listOf(news("LG 트윈스와 두산 베어스 잠실 라이벌전", "양 팀 선발 흐름", "https://news.example.com/accepted-both")),
        )

        postOutlook()
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.newsReferences", hasSize<Any>(1)) }
            .andExpect { jsonPath("$.data.newsReferences[0].url") { value("https://news.example.com/accepted-both") } }
    }

    @Test
    fun `all unrelated news returns empty references and still succeeds`() {
        naverNewsClient.itemsByQuery = mutableMapOf(
            "LG 트윈스 두산 베어스 야구" to listOf(news("삼성 라이온즈와 KIA 타이거즈", "최형우 이야기", "https://news.example.com/unrelated")),
        )

        postOutlook()
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data.newsReferences", hasSize<Any>(0)) }
            .andExpect { jsonPath("$.data.summary") { value("내 직관 기록으로 오늘 경기를 더 재미있게 볼 포인트를 정리했어요.") } }
    }

    @Test
    fun `LLM output mentioning unrelated team triggers template fallback`() {
        naverNewsClient.itemsByQuery = mutableMapOf(
            "LG 트윈스 야구" to listOf(news("LG 트윈스 잠실 훈련", "엘지 타선 점검", "https://news.example.com/accepted-lg")),
        )
        groqClient.content = """
            {
              "points": [
                {"title": "최근 분위기", "body": "삼성과 KIA, 최형우 흐름을 함께 보면 좋아요."},
                {"title": "내 직관 기록", "body": "직관 기록을 함께 확인해요."},
                {"title": "응원 포인트", "body": "응원 흐름을 즐겨보세요."}
              ]
            }
        """.trimIndent()

        val result = postOutlook()
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.generatedBy") { value("template") } }
            .andReturn()

        val body = result.response.contentAsString
        listOf("삼성", "KIA", "최형우").forEach {
            assertFalse(body.contains(it), "AI fallback leaked unrelated term: $it")
        }
    }

    @Test
    fun `newsReferences cannot contain filtered-out URLs`() {
        naverNewsClient.itemsByQuery = mutableMapOf(
            "LG 트윈스 야구" to listOf(news("LG 트윈스 잠실 훈련", "엘지 타선 점검", "https://news.example.com/accepted-lg")),
            "두산 베어스 야구" to listOf(news("삼성 라이온즈와 KIA 타이거즈", "최형우 이야기", "https://news.example.com/filtered-out")),
        )

        val body = postOutlook()
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.newsReferences", hasSize<Any>(1)) }
            .andReturn()
            .response.contentAsString

        assertTrue(body.contains("https://news.example.com/accepted-lg"))
        assertFalse(body.contains("https://news.example.com/filtered-out"))
    }

    @Test
    fun `summary does not contain reference game info wording`() {
        val body = postOutlook()
            .andExpect { status { isOk() } }
            .andReturn()
            .response.contentAsString

        assertFalse(body.contains("참고용 경기 정보"))
    }

    @Test
    fun `response does not contain betting or probability terms`() {
        val body = postOutlook()
            .andExpect { status { isOk() } }
            .andReturn()
            .response.contentAsString

        listOf("베팅", "배당", "확률", "적중률", "odds", "moneyline", "spread").forEach {
            assertFalse(body.contains(it, ignoreCase = true), "Unsafe term present: $it")
        }
    }

    private fun postOutlook() =
        mockMvc.post("/api/v1/match-outlook") {
            header("X-Device-ID", "match-outlook-ai-device")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "favoriteTeamID": "lg-twins",
                  "opponentTeamID": "doosan-bears",
                  "date": "2026-05-07",
                  "stadiumName": "잠실야구장"
                }
            """.trimIndent()
        }

    @TestConfiguration
    class TestBeans {
        @Bean
        @Primary
        fun fakeGroqClient(): FakeGroqClient = FakeGroqClient()

        @Bean
        @Primary
        fun fakeNaverNewsClient(): FakeNaverNewsClient = FakeNaverNewsClient()
    }

    class FakeGroqClient : GroqClient() {
        var content: String = "{}"

        override fun requestDraft(apiKey: String, model: String, prompt: String, timeout: Duration): GroqDraftResult =
            GroqDraftResult(content = content)
    }

    class FakeNaverNewsClient : NaverNewsClient() {
        var itemsByQuery: MutableMap<String, List<NaverNewsItem>> = mutableMapOf()
        val queries = mutableListOf<String>()

        fun reset() {
            itemsByQuery = mutableMapOf()
            queries.clear()
        }

        override fun search(
            baseUrl: String,
            clientId: String,
            clientSecret: String,
            query: String,
            limit: Int,
            timeout: Duration,
        ): List<NaverNewsItem> {
            queries += query
            itemsByQuery[query]?.let { return it.take(limit) }
            if (itemsByQuery.isNotEmpty()) return emptyList()
            val slug = when {
                query.contains("LG") && query.contains("두산") -> "matchup"
                query.contains("LG") -> "lg"
                query.contains("두산") -> "doosan"
                else -> "kbo"
            }
            return (1..limit).map { index ->
                NaverNewsItem(
                    title = "$query 헤드라인 $index",
                    originallink = "https://news.example.com/$slug-$index",
                    link = "https://sports.news.naver.com/$slug-$index",
                    description = "$query 요약 $index",
                    pubDate = "Wed, 06 May 2026 12:00:00 +0900",
                )
            }
        }

        companion object {
            val SUPPLIED_URLS = (1..3).flatMap { index ->
                listOf(
                    "https://news.example.com/matchup-$index",
                    "https://news.example.com/lg-$index",
                    "https://news.example.com/doosan-$index",
                )
            }.toSet()
        }
    }

    companion object {
        private fun validLgDoosanAiContent(): String = """
            {
              "points": [
                {"title": "최근 분위기", "body": "LG와 두산 관련 최신 헤드라인을 보면 잠실 라이벌전 분위기를 살펴보기 좋아요."},
                {"title": "내 직관 기록", "body": "아직 기록이 적어 오늘 경기를 새 기준으로 남겨볼 만해요."},
                {"title": "응원 포인트", "body": "초반 흐름과 응원석 분위기를 함께 보며 경기를 즐겨보세요."}
              ]
            }
        """.trimIndent()

        private fun news(title: String, summary: String, url: String): NaverNewsItem =
            NaverNewsItem(
                title = title,
                originallink = url,
                link = url,
                description = summary,
                pubDate = "Wed, 06 May 2026 12:00:00 +0900",
            )
    }
}
