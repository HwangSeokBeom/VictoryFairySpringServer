package com.victoryfairy.server.seasons

import com.victoryfairy.server.attendance.AttendanceLogRepository
import com.victoryfairy.server.config.AppProperties
import com.victoryfairy.server.kbo.KboGameRepository
import org.springframework.stereotype.Service

@Service
class SeasonService(
    private val properties: AppProperties,
    private val attendanceLogRepository: AttendanceLogRepository,
    private val kboGameRepository: KboGameRepository,
) {
    fun availableSeasons(deviceID: String?): SeasonsData {
        val currentSeason = properties.kbo.scrapedDev.season
        val attendanceSeasons = deviceID
            ?.takeIf { it.isNotBlank() }
            ?.let { attendanceLogRepository.findDistinctSeasonsByDeviceID(it).toSet() }
            ?: emptySet()
        val kboSeasons = kboGameRepository.findDistinctSeasons().toSet()

        val seasons = (setOf(currentSeason, currentSeason - 1) + attendanceSeasons + kboSeasons)
            .filter { it > 0 }
            .sortedDescending()

        return SeasonsData(
            currentSeason = currentSeason,
            items = seasons.map { season ->
                SeasonItem(
                    season = season,
                    label = "${season} 시즌",
                    hasRecords = season in attendanceSeasons || season in kboSeasons,
                )
            },
        )
    }
}
