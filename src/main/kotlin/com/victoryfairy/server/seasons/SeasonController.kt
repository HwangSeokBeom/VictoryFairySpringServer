package com.victoryfairy.server.seasons

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.device.DEVICE_ID_HEADER
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SeasonController(private val service: SeasonService) {
    @GetMapping("/api/v1/seasons")
    fun seasons(request: HttpServletRequest): ApiResponse<SeasonsData> =
        ApiResponse.ok(service.availableSeasons(request.getHeader(DEVICE_ID_HEADER)?.trim()))
}
