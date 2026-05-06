package com.victoryfairy.server.community

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface CommunityPostRepository : JpaRepository<CommunityPostEntity, UUID> {
    fun findByStatusOrderByCreatedAtDesc(status: String): List<CommunityPostEntity>
}
