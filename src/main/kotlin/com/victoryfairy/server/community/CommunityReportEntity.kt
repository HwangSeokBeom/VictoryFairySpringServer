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
    name = "community_reports",
    indexes = [Index(name = "idx_community_reports_post", columnList = "postID")],
    uniqueConstraints = [UniqueConstraint(name = "uk_community_reports_post_device", columnNames = ["postID", "deviceID"])],
)
class CommunityReportEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var postID: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 128)
    var deviceID: String = "",
    @Column(nullable = false, length = 32)
    var reason: String = "other",
    var createdAt: Instant = Instant.now(),
) {
    @PrePersist
    fun prePersist() {
        createdAt = Instant.now()
    }
}
