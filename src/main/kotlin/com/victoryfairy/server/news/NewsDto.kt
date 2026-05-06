package com.victoryfairy.server.news

import com.fasterxml.jackson.annotation.JsonInclude

data class NewsData(
    val items: List<NewsArticleItem>,
    @field:JsonInclude(JsonInclude.Include.ALWAYS)
    val message: String?,
    val sourceDisclosure: String,
)

data class NewsArticleItem(
    val id: String,
    val title: String,
    val summary: String?,
    val sourceName: String,
    val publishedAt: String?,
    val url: String,
    val teamIDs: List<String>,
)
