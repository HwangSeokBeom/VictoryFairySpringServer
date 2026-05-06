package com.victoryfairy.server

import com.victoryfairy.server.common.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @GetMapping("/health")
    fun health(): ApiResponse<Map<String, String>> = ApiResponse.ok(mapOf("status" to "ok"))
}
