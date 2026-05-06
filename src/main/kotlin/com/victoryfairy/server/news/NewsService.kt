package com.victoryfairy.server.news

import com.victoryfairy.server.config.AppProperties
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service

@Service
class NewsService(
    private val properties: AppProperties,
    private val environment: Environment,
    private val naverNewsClient: NaverNewsClient,
) {
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun list(teamID: String?, limit: Int?): NewsData {
        val normalizedLimit = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val newsProperties = properties.news
        val provider = newsProperties.provider.trim().lowercase()
        val newsQuery = NewsTeamQueryMapper.queryFor(teamID)

        if (provider != "naver") {
            return localSample(newsQuery.knownTeamID, normalizedLimit)
        }

        if (newsProperties.naverClientId.isBlank() || newsProperties.naverClientSecret.isBlank()) {
            return if (isProduction()) {
                emptyNews("뉴스 제공 설정이 준비되지 않았습니다.")
            } else {
                localSample(newsQuery.knownTeamID, normalizedLimit, "개발용 샘플 뉴스입니다.")
            }
        }

        val cacheKey = listOf(provider, newsQuery.knownTeamID ?: "all", newsQuery.query, normalizedLimit).joinToString("|")
        freshCached(cacheKey)?.let { return it }

        return runCatching {
            val rawItems = naverNewsClient.search(
                baseUrl = newsProperties.naverNewsBaseUrl,
                clientId = newsProperties.naverClientId,
                clientSecret = newsProperties.naverClientSecret,
                query = newsQuery.query,
                limit = normalizedLimit,
                timeout = REQUEST_TIMEOUT,
            )
            val newsData = NewsData(
                items = NaverNewsNormalizer.normalize(rawItems, newsQuery.knownTeamID).take(normalizedLimit),
                message = null,
                sourceDisclosure = SOURCE_DISCLOSURE,
            )
            cache[cacheKey] = CacheEntry(newsData, Instant.now().plusSeconds(cacheTtlSeconds()))
            newsData
        }.getOrElse {
            cached(cacheKey)
                ?: if (isProduction()) emptyNews("뉴스를 불러오지 못했습니다.")
                else localSample(newsQuery.knownTeamID, normalizedLimit, "뉴스 제공자 연결에 실패해 개발용 샘플을 표시합니다.")
        }
    }

    private fun freshCached(cacheKey: String): NewsData? =
        cache[cacheKey]?.takeIf { Instant.now().isBefore(it.expiresAt) }?.data

    private fun cached(cacheKey: String): NewsData? = cache[cacheKey]?.data

    private fun cacheTtlSeconds(): Long = properties.news.cacheTtlSeconds.coerceAtLeast(1)

    private fun emptyNews(message: String?): NewsData =
        NewsData(
            items = emptyList(),
            message = message,
            sourceDisclosure = SOURCE_DISCLOSURE,
        )

    private fun localSample(teamID: String?, limit: Int, message: String = "개발용 샘플 뉴스입니다."): NewsData {
        val items = listOf(
            NewsArticleItem(
                id = "local-kbo-news-sample",
                title = "KBO 야구 뉴스",
                summary = "서버에 Naver 뉴스 제공자를 설정하면 팀별 야구 뉴스가 표시됩니다.",
                sourceName = "개발용 샘플",
                publishedAt = null,
                url = "https://sports.news.naver.com/kbaseball/index",
                teamIDs = listOfNotNull(teamID),
            ),
        ).take(limit)
        return NewsData(
            items = items,
            message = message,
            sourceDisclosure = SOURCE_DISCLOSURE,
        )
    }

    private fun isProduction(): Boolean =
        environment.activeProfiles.any { it.equals("prod", true) || it.equals("production", true) } ||
            System.getenv("NODE_ENV").equals("production", ignoreCase = true)

    private data class CacheEntry(
        val data: NewsData,
        val expiresAt: Instant,
    )

    companion object {
        const val SOURCE_DISCLOSURE = "뉴스는 외부 매체로 이동해 확인해 주세요."
        private const val DEFAULT_LIMIT = 20
        private const val MAX_LIMIT = 20
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(7)
    }
}
