package com.victoryfairy.server.statistics

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.victoryfairy.server.attendance.AttendanceLogRepository
import com.victoryfairy.server.attendance.AttendanceLogService
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito

class StatisticsServiceTest {
    @Test
    fun `winRate excludes draw and canceled`() {
        val attendanceLogService = AttendanceLogService(Mockito.mock(AttendanceLogRepository::class.java), jacksonObjectMapper())
        val service = StatisticsService(attendanceLogService)
        assertEquals(0.5, service.winRate(1, 1))
        assertEquals(null, service.winRate(0, 0))
    }
}
