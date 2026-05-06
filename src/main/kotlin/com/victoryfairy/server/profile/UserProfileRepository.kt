package com.victoryfairy.server.profile

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface UserProfileRepository : JpaRepository<UserProfileEntity, UUID> {
    fun findByDeviceID(deviceID: String): UserProfileEntity?
    fun findByPublicAuthorID(publicAuthorID: String): UserProfileEntity?
    fun findByPublicAuthorIDIn(publicAuthorIDs: Collection<String>): List<UserProfileEntity>
    fun existsByDeviceID(deviceID: String): Boolean
}
