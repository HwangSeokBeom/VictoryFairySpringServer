package com.victoryfairy.server.news

import com.victoryfairy.server.common.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class NewsController(private val service: NewsService) {
    @GetMapping("/api/v1/news")
    fun news(
        @RequestParam(required = false) teamID: String?,
        @RequestParam(required = false) limit: Int?,
    ): ApiResponse<NewsData> = ApiResponse.ok(service.list(teamID, limit))
}
