package com.victoryfairy.server.feed

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.device.DeviceIdentityFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class FeedController(
    private val service: FeedService,
    private val deviceIdentityFilter: DeviceIdentityFilter,
) {
    @GetMapping("/api/v1/feed")
    fun feed(request: HttpServletRequest, @RequestParam(required = false) season: Int?, @RequestParam(required = false) result: String?): ApiResponse<Map<String, List<Any>>> {
        @Suppress("UNCHECKED_CAST")
        return ApiResponse.ok(service.feed(deviceIdentityFilter.requireDeviceID(request), season, result) as Map<String, List<Any>>)
    }
}
