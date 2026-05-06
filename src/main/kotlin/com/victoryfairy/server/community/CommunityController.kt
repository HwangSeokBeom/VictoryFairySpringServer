package com.victoryfairy.server.community

import com.victoryfairy.server.common.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/community/posts")
class CommunityController(private val service: CommunityService) {
    @GetMapping
    fun posts(): ApiResponse<CommunityPostsData> = ApiResponse.ok(service.list())

    @PostMapping
    fun create(@RequestBody request: CommunityPostRequest): ApiResponse<CommunityPostResponse> =
        ApiResponse.ok(service.create(request))
}
