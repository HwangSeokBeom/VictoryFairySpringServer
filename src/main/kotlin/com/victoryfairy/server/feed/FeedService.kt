package com.victoryfairy.server.feed

import com.victoryfairy.server.attendance.AttendanceLogDto
import com.victoryfairy.server.attendance.AttendanceLogService
import org.springframework.stereotype.Service

@Service
class FeedService(private val attendanceLogService: AttendanceLogService) {
    fun feed(deviceID: String, season: Int?, result: String?): Map<String, List<AttendanceLogDto>> =
        mapOf("items" to attendanceLogService.list(deviceID, season, result))
}
