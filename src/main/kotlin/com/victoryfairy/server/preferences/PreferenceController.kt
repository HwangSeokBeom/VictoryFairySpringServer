package com.victoryfairy.server.preferences

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.device.DeviceIdentityFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me/preferences")
class PreferenceController(
    private val preferenceService: PreferenceService,
    private val deviceIdentityFilter: DeviceIdentityFilter,
) {
    @GetMapping
    fun get(request: HttpServletRequest): ApiResponse<PreferenceDto> =
        ApiResponse.ok(preferenceService.get(deviceIdentityFilter.requireDeviceID(request)))

    @PutMapping
    fun put(request: HttpServletRequest, @RequestBody body: PreferenceRequest): ApiResponse<PreferenceDto> =
        ApiResponse.ok(preferenceService.put(deviceIdentityFilter.requireDeviceID(request), body))
}
