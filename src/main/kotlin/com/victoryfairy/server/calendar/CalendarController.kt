package com.victoryfairy.server.calendar

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.device.DeviceIdentityFilter
import jakarta.servlet.http.HttpServletRequest
import java.time.Clock
import java.time.LocalDate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class CalendarController(
    private val service: CalendarService,
    private val deviceIdentityFilter: DeviceIdentityFilter,
    private val clock: Clock,
) {
    @GetMapping("/api/v1/calendar")
    fun calendar(
        request: HttpServletRequest,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
    ): ApiResponse<Map<String, Any>> {
        val today = LocalDate.now(clock)
        return ApiResponse.ok(
            service.month(
                deviceIdentityFilter.requireDeviceID(request),
                year ?: today.year,
                month ?: today.monthValue,
            ),
        )
    }
}
