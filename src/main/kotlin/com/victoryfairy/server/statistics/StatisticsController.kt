package com.victoryfairy.server.statistics

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.device.DeviceIdentityFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class StatisticsController(
    private val service: StatisticsService,
    private val deviceIdentityFilter: DeviceIdentityFilter,
) {
    @GetMapping("/api/v1/statistics/summary")
    fun summary(request: HttpServletRequest, @RequestParam season: Int): ApiResponse<Map<String, Any?>> =
        ApiResponse.ok(service.summary(deviceIdentityFilter.requireDeviceID(request), season))

    @GetMapping("/api/v1/statistics/stadiums")
    fun stadiums(request: HttpServletRequest, @RequestParam season: Int): ApiResponse<Map<String, Any>> =
        ApiResponse.ok(service.stadiums(deviceIdentityFilter.requireDeviceID(request), season))

    @GetMapping("/api/v1/statistics/opponents")
    fun opponents(request: HttpServletRequest, @RequestParam season: Int): ApiResponse<Map<String, Any>> =
        ApiResponse.ok(service.opponents(deviceIdentityFilter.requireDeviceID(request), season))
}
