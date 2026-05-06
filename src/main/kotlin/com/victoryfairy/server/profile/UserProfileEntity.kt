package com.victoryfairy.server.profile

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "user_profiles",
    indexes = [Index(name = "idx_user_profiles_device", columnList = "deviceID")],
    uniqueConstraints = [UniqueConstraint(name = "uk_user_profiles_device", columnNames = ["deviceID"])],
)
class UserProfileEntity(
    @Id
    @GeneratedValue
    var id: UUID? = null,
    @Column(nullable = false, length = 128)
    var deviceID: String = "",
    @Column(length = 80, unique = true)
    var publicAuthorID: String? = null,
    @Column(nullable = false, length = 12)
    var nickname: String = "",
    @Column(nullable = false, length = 60)
    var favoriteTeamID: String = "",
    @Column(length = 16)
    var profileEmoji: String? = null,
    @Column(length = 160)
    var profileImagePath: String? = null,
    @Column(length = 80)
    var profileImageMimeType: String? = null,
    var profileImageUpdatedAt: Instant? = null,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
) {
    @PrePersist
    fun prePersist() {
        val now = Instant.now()
        ensurePublicAuthorID()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        ensurePublicAuthorID()
        updatedAt = Instant.now()
    }

    fun ensurePublicAuthorID(): String {
        val existing = publicAuthorID?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val generated = "author_${UUID.randomUUID().toString().replace("-", "")}"
        publicAuthorID = generated
        return generated
    }
}
