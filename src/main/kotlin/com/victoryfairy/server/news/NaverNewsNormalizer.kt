package com.victoryfairy.server.news

import java.net.URI
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.jsoup.Jsoup

object NaverNewsNormalizer {
    fun normalize(items: List<NaverNewsItem>, teamID: String?): List<NewsArticleItem> =
        items.mapNotNull { item -> normalize(item, teamID) }

    private fun normalize(item: NaverNewsItem, teamID: String?): NewsArticleItem? {
        val title = plainText(item.title).takeIf { it.isNotBlank() } ?: return null
        val url = preferredUrl(item.originallink, item.link) ?: return null
        val publishedAt = parsePublishedAt(item.pubDate)
        val id = "naver-${stableHash("$url|$title|${item.pubDate.orEmpty()}")}"

        return NewsArticleItem(
            id = id,
            title = title,
            summary = plainText(item.description).takeIf { it.isNotBlank() },
            sourceName = "네이버 뉴스",
            publishedAt = publishedAt,
            url = url,
            teamIDs = listOfNotNull(teamID),
        )
    }

    private fun plainText(value: String?): String =
        Jsoup.parse(value.orEmpty()).text().trim()

    private fun preferredUrl(originalLink: String?, link: String?): String? =
        listOf(originalLink, link)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull(::isHttpUrl)

    private fun isHttpUrl(value: String): Boolean =
        runCatching {
            val scheme = URI(value).scheme?.lowercase()
            scheme == "http" || scheme == "https"
        }.getOrDefault(false)

    private fun parsePublishedAt(value: String?): String? =
        value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                runCatching {
                    ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                }.getOrNull()
            }

    private fun stableHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}
