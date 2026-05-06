package com.victoryfairy.server.attendance

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.device.DeviceIdentityFilter
import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/attendance-logs")
class AttendanceLogController(
    private val service: AttendanceLogService,
    private val deviceIdentityFilter: DeviceIdentityFilter,
) {
    @PostMapping
    fun create(request: HttpServletRequest, @RequestBody body: AttendanceLogRequest): ApiResponse<AttendanceLogDto> =
        ApiResponse.ok(service.create(deviceIdentityFilter.requireDeviceID(request), body))

    @GetMapping
    fun list(request: HttpServletRequest, @RequestParam(required = false) season: Int?, @RequestParam(required = false) result: String?): ApiResponse<AttendanceLogListResponse> =
        ApiResponse.ok(AttendanceLogListResponse(service.list(deviceIdentityFilter.requireDeviceID(request), season, result)))

    @GetMapping("/{id}")
    fun get(request: HttpServletRequest, @PathVariable id: UUID): ApiResponse<AttendanceLogDto> =
        ApiResponse.ok(service.get(deviceIdentityFilter.requireDeviceID(request), id))

    @PutMapping("/{id}")
    fun update(request: HttpServletRequest, @PathVariable id: UUID, @RequestBody body: AttendanceLogRequest): ApiResponse<AttendanceLogDto> =
        ApiResponse.ok(service.update(deviceIdentityFilter.requireDeviceID(request), id, body))

    @DeleteMapping("/{id}")
    fun delete(request: HttpServletRequest, @PathVariable id: UUID): ApiResponse<Map<String, Boolean>> {
        service.delete(deviceIdentityFilter.requireDeviceID(request), id)
        return ApiResponse.ok(mapOf("deleted" to true))
    }
}
