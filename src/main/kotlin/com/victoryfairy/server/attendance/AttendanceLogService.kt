package com.victoryfairy.server.attendance

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.teams.TeamSeed
import jakarta.transaction.Transactional
import java.time.LocalDate
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class AttendanceLogService(
    private val repository: AttendanceLogRepository,
    private val objectMapper: ObjectMapper,
) {
    fun list(deviceID: String, season: Int?, result: String?): List<AttendanceLogDto> {
        val logs = when {
            season != null && result != null -> repository.findByDeviceIDAndSeasonAndResultOrderByDateDescCreatedAtDesc(deviceID, season, result)
            season != null -> repository.findByDeviceIDAndSeasonOrderByDateDescCreatedAtDesc(deviceID, season)
            else -> repository.findByDeviceIDOrderByDateDescCreatedAtDesc(deviceID)
        }
        return logs.map(::toDto)
    }

    fun get(deviceID: String, id: UUID): AttendanceLogDto = toDto(owned(deviceID, id))

    @Transactional
    fun create(deviceID: String, request: AttendanceLogRequest): AttendanceLogDto =
        toDto(repository.save(applyRequest(AttendanceLogEntity(deviceID = deviceID), request)))

    @Transactional
    fun update(deviceID: String, id: UUID, request: AttendanceLogRequest): AttendanceLogDto =
        toDto(repository.save(applyRequest(owned(deviceID, id), request)))

    @Transactional
    fun delete(deviceID: String, id: UUID) {
        repository.delete(owned(deviceID, id))
    }

    fun seasonLogs(deviceID: String, season: Int): List<AttendanceLogEntity> =
        repository.findByDeviceIDAndSeasonOrderByDateDescCreatedAtDesc(deviceID, season)

    private fun owned(deviceID: String, id: UUID): AttendanceLogEntity {
        val log = repository.findById(id).orElseThrow { ApiException("ATTENDANCE_LOG_NOT_FOUND", "직관 기록을 찾을 수 없습니다.", 404) }
        if (log.deviceID != deviceID) throw ApiException("ATTENDANCE_LOG_NOT_FOUND", "직관 기록을 찾을 수 없습니다.", 404)
        return log
    }

    private fun applyRequest(entity: AttendanceLogEntity, request: AttendanceLogRequest): AttendanceLogEntity {
        validate(request)
        entity.date = LocalDate.parse(request.date ?: request.gameDate ?: throw ApiException("VALIDATION_ERROR", "date가 필요합니다."))
        entity.season = request.season
        entity.favoriteTeamID = request.favoriteTeamID
        entity.opponentTeamID = request.opponentTeamID
        entity.stadiumName = request.stadiumName
        entity.result = request.result
        entity.ourScore = request.favoriteTeamScore ?: request.ourScore
        entity.opponentScore = request.opponentTeamScore ?: request.opponentScore
        entity.memo = request.memo ?: request.shortMemo
        entity.diaryText = request.diaryText
        entity.seatText = request.seatText
        entity.companionText = request.companionText ?: request.companionType
        entity.highlightTagsJson = objectMapper.writeValueAsString(request.highlightTags.take(10))
        entity.linkedKBOGameID = request.linkedKBOGameID
        entity.gameSource = request.gameSource
        entity.sourceLabel = request.sourceLabel
        return entity
    }

    private fun validate(request: AttendanceLogRequest) {
        if (TeamSeed.find(request.favoriteTeamID) == null || TeamSeed.find(request.opponentTeamID) == null) {
            throw ApiException("VALIDATION_ERROR", "존재하지 않는 팀입니다.")
        }
        if (request.favoriteTeamID == request.opponentTeamID) throw ApiException("VALIDATION_ERROR", "응원팀과 상대팀은 같을 수 없습니다.")
        if (request.result !in setOf("win", "loss", "draw", "canceled")) throw ApiException("VALIDATION_ERROR", "경기 결과가 올바르지 않습니다.")
        val our = request.favoriteTeamScore ?: request.ourScore
        val opponent = request.opponentTeamScore ?: request.opponentScore
        if (our != null && opponent != null) {
            val mismatch = (request.result == "win" && our <= opponent) || (request.result == "loss" && our >= opponent) || (request.result == "draw" && our != opponent)
            if (mismatch) throw ApiException("SCORE_RESULT_MISMATCH", "점수와 경기 결과가 일치하지 않습니다.")
        }
    }

    fun toDto(log: AttendanceLogEntity): AttendanceLogDto {
        val favorite = TeamSeed.find(log.favoriteTeamID)?.shortName ?: log.favoriteTeamID
        val opponent = TeamSeed.find(log.opponentTeamID)?.shortName ?: log.opponentTeamID
        val resultLabel = mapOf("win" to "승", "loss" to "패", "draw" to "무", "canceled" to "취소")[log.result] ?: log.result
        val scoreText = if (log.result == "canceled") "취소" else if (log.ourScore != null && log.opponentScore != null) "${log.ourScore}:${log.opponentScore} $resultLabel" else resultLabel
        val tags = runCatching { objectMapper.readValue<List<String>>(log.highlightTagsJson) }.getOrDefault(emptyList())
        return AttendanceLogDto(
            id = log.id.toString(),
            deviceID = log.deviceID,
            date = log.date.toString(),
            gameDate = log.date.toString(),
            season = log.season,
            favoriteTeamID = log.favoriteTeamID,
            opponentTeamID = log.opponentTeamID,
            stadiumName = log.stadiumName,
            result = log.result,
            ourScore = log.ourScore,
            opponentScore = log.opponentScore,
            favoriteTeamScore = log.ourScore,
            opponentTeamScore = log.opponentScore,
            memo = log.memo,
            shortMemo = log.memo,
            diaryText = log.diaryText,
            seatText = log.seatText,
            companionText = log.companionText,
            companionType = log.companionText,
            highlightTags = tags,
            linkedKBOGameID = log.linkedKBOGameID,
            gameSource = log.gameSource,
            sourceLabel = log.sourceLabel,
            matchupText = "$favorite vs $opponent",
            scoreText = scoreText,
            createdAt = log.createdAt.toString(),
            updatedAt = log.updatedAt.toString(),
        )
    }
}
