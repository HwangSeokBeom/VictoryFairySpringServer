package com.victoryfairy.server.matchoutlook

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.device.DeviceIdentityFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class MatchOutlookController(
    private val service: MatchOutlookService,
    private val deviceIdentityFilter: DeviceIdentityFilter,
) {
    @PostMapping("/api/v1/match-outlook")
    fun outlook(request: HttpServletRequest, @RequestBody body: MatchOutlookRequest): ApiResponse<MatchOutlookData> =
        ApiResponse.ok(service.create(deviceIdentityFilter.optionalDeviceID(request), body))
}
