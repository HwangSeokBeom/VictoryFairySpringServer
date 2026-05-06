package com.victoryfairy.server.community

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "community_blocks",
    indexes = [
        Index(name = "idx_community_blocks_blocker", columnList = "blockerDeviceID"),
        Index(name = "idx_community_blocks_blocked_author", columnList = "blockedAuthorID"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_community_blocks_blocker_author", columnNames = ["blockerDeviceID", "blockedAuthorID"]),
    ],
)
class CommunityBlockEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 128)
    var blockerDeviceID: String = "",
    @Column(nullable = false, length = 80)
    var blockedAuthorID: String = "",
    var createdAt: Instant = Instant.now(),
) {
    @PrePersist
    fun prePersist() {
        createdAt = Instant.now()
    }
}
