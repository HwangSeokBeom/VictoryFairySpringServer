package com.victoryfairy.server.calendar

import com.victoryfairy.server.attendance.AttendanceLogService
import org.springframework.stereotype.Service

@Service
class CalendarService(private val attendanceLogService: AttendanceLogService) {
    fun month(deviceID: String, year: Int, month: Int): Map<String, Any> {
        val logs = attendanceLogService.list(deviceID, null, null).filter { it.date.startsWith("%04d-%02d".format(year, month)) }
        val days = logs.groupBy { it.date }.toSortedMap().map { (date, dayLogs) -> mapOf("date" to date, "logs" to dayLogs) }
        return mapOf(
            "year" to year,
            "month" to month,
            "previousMonth" to adjacentMonth(year, month, -1),
            "nextMonth" to adjacentMonth(year, month, 1),
            "summary" to count(logs.map { it.result }),
            "days" to days,
        )
    }

    private fun adjacentMonth(year: Int, month: Int, delta: Int): Map<String, Int> {
        val nextMonthIndex = month - 1 + delta
        val nextYear = year + Math.floorDiv(nextMonthIndex, 12)
        val normalizedMonth = Math.floorMod(nextMonthIndex, 12) + 1
        return mapOf("year" to nextYear, "month" to normalizedMonth)
    }

    private fun count(results: List<String>): Map<String, Int> = mapOf(
        "totalGames" to results.size,
        "wins" to results.count { it == "win" },
        "losses" to results.count { it == "loss" },
        "draws" to results.count { it == "draw" },
        "canceled" to results.count { it == "canceled" },
    )
}
