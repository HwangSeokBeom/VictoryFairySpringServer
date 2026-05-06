package com.victoryfairy.server.community

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface CommunityBlockRepository : JpaRepository<CommunityBlockEntity, UUID> {
    fun existsByBlockerDeviceIDAndBlockedAuthorID(blockerDeviceID: String, blockedAuthorID: String): Boolean
    fun findByBlockerDeviceIDOrderByCreatedAtDesc(blockerDeviceID: String): List<CommunityBlockEntity>
    fun deleteByBlockerDeviceIDAndBlockedAuthorID(blockerDeviceID: String, blockedAuthorID: String)
}
