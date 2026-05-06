package com.victoryfairy.server.news

import com.victoryfairy.server.config.AppProperties
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

class NewsServiceTest {
    @Test
    fun `teamID maps to Korean query`() {
        assertEquals("삼성 라이온즈 야구", NewsTeamQueryMapper.queryFor("samsung-lions").query)
        assertEquals("KIA 타이거즈 야구", NewsTeamQueryMapper.queryFor("kia-tigers").query)
    }

    @Test
    fun `unknown teamID uses KBO baseball query`() {
        val query = NewsTeamQueryMapper.queryFor("unknown-team")

        assertEquals("KBO 야구", query.query)
        assertNull(query.knownTeamID)
    }

    @Test
    fun `Naver response strips HTML from title and description`() {
        val item = normalizeOne(
            NaverNewsItem(
                title = "<b>삼성</b> &amp; KIA",
                originallink = "https://example.com/original",
                link = "https://example.com/naver",
                description = "오늘 <b>야구</b> 소식 &quot;요약&quot;",
                pubDate = "Wed, 06 May 2026 12:00:00 +0900",
            ),
        )

        assertEquals("삼성 & KIA", item.title)
        assertEquals("오늘 야구 소식 \"요약\"", item.summary)
    }

    @Test
    fun `originallink is preferred over link`() {
        val item = normalizeOne(
            NaverNewsItem(
                title = "삼성 라이온즈",
                originallink = "https://publisher.example.com/article",
                link = "https://news.naver.com/article",
                description = "요약",
                pubDate = "Wed, 06 May 2026 12:00:00 +0900",
            ),
        )

        assertEquals("https://publisher.example.com/article", item.url)
    }

    @Test
    fun `pubDate is parsed safely`() {
        val parsed = normalizeOne(
            NaverNewsItem(
                title = "삼성 라이온즈",
                originallink = "https://example.com/parsed",
                description = "요약",
                pubDate = "Wed, 06 May 2026 12:00:00 +0900",
            ),
        )
        val invalid = normalizeOne(
            NaverNewsItem(
                title = "삼성 라이온즈",
                originallink = "https://example.com/invalid",
                description = "요약",
                pubDate = "not a date",
            ),
        )

        assertEquals("2026-05-06T12:00:00+09:00", parsed.publishedAt)
        assertNull(invalid.publishedAt)
    }

    @Test
    fun `credentials missing fallback does not throw in production`() {
        val service = NewsService(
            properties = AppProperties(news = AppProperties.NewsProperties(provider = "naver")),
            environment = MockEnvironment().also { it.setActiveProfiles("production") },
            naverNewsClient = FakeNaverNewsClient(),
        )

        val data = service.list("samsung-lions", 20)

        assertTrue(data.items.isEmpty())
        assertEquals("뉴스 제공 설정이 준비되지 않았습니다.", data.message)
        assertEquals(NewsService.SOURCE_DISCLOSURE, data.sourceDisclosure)
    }

    @Test
    fun `cache returns repeated result without second provider call`() {
        val client = FakeNaverNewsClient()
        val service = NewsService(
            properties = AppProperties(
                news = AppProperties.NewsProperties(
                    provider = "naver",
                    naverClientId = "replace-with-naver-client-id",
                    naverClientSecret = "replace-with-naver-client-secret",
                    cacheTtlSeconds = 1800,
                ),
            ),
            environment = MockEnvironment(),
            naverNewsClient = client,
        )

        val first = service.list("samsung-lions", 20)
        val second = service.list("samsung-lions", 20)

        assertEquals(1, client.calls)
        assertEquals(first.items, second.items)
        assertNull(first.message)
        assertEquals(NewsService.SOURCE_DISCLOSURE, first.sourceDisclosure)
    }

    private fun normalizeOne(item: NaverNewsItem): NewsArticleItem =
        NaverNewsNormalizer.normalize(listOf(item), "samsung-lions").single()

    private class FakeNaverNewsClient : NaverNewsClient() {
        var calls: Int = 0

        override fun search(
            baseUrl: String,
            clientId: String,
            clientSecret: String,
            query: String,
            limit: Int,
            timeout: Duration,
        ): List<NaverNewsItem> {
            calls += 1
            return listOf(
                NaverNewsItem(
                    title = "<b>$query</b> 새 소식 $calls",
                    originallink = "https://example.com/news-$calls",
                    link = "https://news.naver.com/news-$calls",
                    description = "팀 소식 요약",
                    pubDate = "Wed, 06 May 2026 12:00:00 +0900",
                ),
            )
        }
    }
}
