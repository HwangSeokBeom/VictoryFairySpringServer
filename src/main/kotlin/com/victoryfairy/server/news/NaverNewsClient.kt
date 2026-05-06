package com.victoryfairy.server.news

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Duration
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

@Component
open class NaverNewsClient {
    open fun search(
        baseUrl: String,
        clientId: String,
        clientSecret: String,
        query: String,
        limit: Int,
        timeout: Duration,
    ): List<NaverNewsItem> {
        val uri = UriComponentsBuilder.fromUriString(baseUrl)
            .queryParam("query", query)
            .queryParam("display", limit)
            .queryParam("start", 1)
            .queryParam("sort", "date")
            .build()
            .encode()
            .toUri()

        return WebClient.builder()
            .build()
            .get()
            .uri(uri)
            .header("X-Naver-Client-Id", clientId)
            .header("X-Naver-Client-Secret", clientSecret)
            .retrieve()
            .bodyToMono(NaverNewsResponse::class.java)
            .block(timeout)
            ?.items
            ?: emptyList()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNewsResponse(
    val items: List<NaverNewsItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverNewsItem(
    val title: String? = null,
    val originallink: String? = null,
    val link: String? = null,
    val description: String? = null,
    val pubDate: String? = null,
)
