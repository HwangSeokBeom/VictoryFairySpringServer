package com.victoryfairy.server.community

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface CommunityReportRepository : JpaRepository<CommunityReportEntity, UUID> {
    fun existsByPostIDAndDeviceID(postID: UUID, deviceID: String): Boolean
}
