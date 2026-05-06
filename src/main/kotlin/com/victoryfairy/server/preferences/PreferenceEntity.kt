package com.victoryfairy.server.preferences

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "preferences")
class PreferenceEntity(
    @Id
    @Column(length = 128)
    var deviceID: String = "",
    @Column(length = 60)
    var favoriteTeamID: String? = null,
    var selectedSeason: Int = 2026,
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
