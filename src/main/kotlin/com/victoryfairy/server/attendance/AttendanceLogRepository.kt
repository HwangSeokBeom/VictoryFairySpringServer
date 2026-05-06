package com.victoryfairy.server.attendance

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface AttendanceLogRepository : JpaRepository<AttendanceLogEntity, UUID> {
    fun findByDeviceIDOrderByDateDescCreatedAtDesc(deviceID: String): List<AttendanceLogEntity>
    fun findByDeviceIDAndSeasonOrderByDateDescCreatedAtDesc(deviceID: String, season: Int): List<AttendanceLogEntity>
    fun findByDeviceIDAndSeasonAndResultOrderByDateDescCreatedAtDesc(deviceID: String, season: Int, result: String): List<AttendanceLogEntity>
}
