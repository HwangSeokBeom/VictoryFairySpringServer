package com.victoryfairy.server.community

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "community_posts",
    indexes = [
        Index(name = "idx_community_posts_status_created", columnList = "status, createdAt"),
        Index(name = "idx_community_posts_team", columnList = "teamID"),
        Index(name = "idx_community_posts_author", columnList = "authorDeviceID"),
        Index(name = "idx_community_posts_author_id", columnList = "authorID"),
    ],
)
class CommunityPostEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 60)
    var teamID: String = "",
    @Column(nullable = false, length = 128)
    var authorDeviceID: String = "",
    @Column(length = 80)
    var authorID: String? = null,
    @Column(nullable = false, length = 12)
    var authorDisplayName: String = "",
    @Column(nullable = false, length = 300)
    var content: String = "",
    @Column(nullable = false, length = 20)
    var status: String = "visible",
    var likeCount: Int = 0,
    var reportCount: Int = 0,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
) {
    @PrePersist
    fun prePersist() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }
}
