package com.victoryfairy.server.calendar

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.device.DeviceIdentityFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class CalendarController(
    private val service: CalendarService,
    private val deviceIdentityFilter: DeviceIdentityFilter,
) {
    @GetMapping("/api/v1/calendar")
    fun calendar(request: HttpServletRequest, @RequestParam year: Int, @RequestParam month: Int): ApiResponse<Map<String, Any>> =
        ApiResponse.ok(service.month(deviceIdentityFilter.requireDeviceID(request), year, month))
}
