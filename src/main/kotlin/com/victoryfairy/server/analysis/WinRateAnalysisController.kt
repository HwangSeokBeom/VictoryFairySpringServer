package com.victoryfairy.server.analysis

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.config.AppProperties
import com.victoryfairy.server.device.DeviceIdentityFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class WinRateAnalysisController(
    private val service: WinRateAnalysisService,
    private val deviceIdentityFilter: DeviceIdentityFilter,
    private val properties: AppProperties,
) {
    @GetMapping("/api/v1/analysis/win-rate")
    fun winRate(request: HttpServletRequest, @RequestParam(required = false) season: Int?): ApiResponse<WinRateAnalysisData> =
        ApiResponse.ok(service.analyze(deviceIdentityFilter.requireDeviceID(request), season ?: properties.kbo.scrapedDev.season))
}
