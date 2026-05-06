package com.victoryfairy.server.attendance

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(
    name = "attendance_logs",
    indexes = [
        Index(name = "idx_attendance_device", columnList = "deviceID"),
        Index(name = "idx_attendance_season", columnList = "season"),
        Index(name = "idx_attendance_date", columnList = "date"),
    ],
)
class AttendanceLogEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 128)
    var deviceID: String = "",
    @Column(nullable = false)
    var date: LocalDate = LocalDate.now(),
    var season: Int = 0,
    @Column(nullable = false, length = 60)
    var favoriteTeamID: String = "",
    @Column(nullable = false, length = 60)
    var opponentTeamID: String = "",
    @Column(nullable = false, length = 100)
    var stadiumName: String = "",
    @Column(nullable = false, length = 20)
    var result: String = "win",
    var ourScore: Int? = null,
    var opponentScore: Int? = null,
    @Column(length = 300)
    var memo: String? = null,
    @Column(columnDefinition = "TEXT")
    var diaryText: String? = null,
    @Column(length = 100)
    var seatText: String? = null,
    @Column(length = 100)
    var companionText: String? = null,
    @Column(columnDefinition = "TEXT")
    var highlightTagsJson: String = "[]",
    @Column(length = 80)
    var linkedKBOGameID: String? = null,
    @Column(length = 30)
    var gameSource: String? = null,
    @Column(length = 80)
    var sourceLabel: String? = null,
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
