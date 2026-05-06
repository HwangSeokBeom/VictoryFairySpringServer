package com.victoryfairy.server.attendance

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AttendanceLogRepository : JpaRepository<AttendanceLogEntity, UUID> {
    fun findByDeviceIDOrderByDateDescCreatedAtDesc(deviceID: String): List<AttendanceLogEntity>
    fun findByDeviceIDAndSeasonOrderByDateDescCreatedAtDesc(deviceID: String, season: Int): List<AttendanceLogEntity>
    fun findByDeviceIDAndSeasonAndResultOrderByDateDescCreatedAtDesc(deviceID: String, season: Int, result: String): List<AttendanceLogEntity>

    @Query("select distinct a.season from AttendanceLogEntity a where a.deviceID = :deviceID")
    fun findDistinctSeasonsByDeviceID(@Param("deviceID") deviceID: String): List<Int>
}
