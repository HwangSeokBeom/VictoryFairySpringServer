package com.victoryfairy.server.preferences

import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.teams.TeamSeed
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class PreferenceService(private val repository: PreferenceRepository) {
    fun get(deviceID: String): PreferenceDto = toDto(repository.findById(deviceID).orElse(PreferenceEntity(deviceID = deviceID)))

    @Transactional
    fun put(deviceID: String, request: PreferenceRequest): PreferenceDto {
        if (request.favoriteTeamID != null && TeamSeed.find(request.favoriteTeamID) == null) {
            throw ApiException("VALIDATION_ERROR", "존재하지 않는 팀입니다.")
        }
        val entity = repository.findById(deviceID).orElse(PreferenceEntity(deviceID = deviceID))
        entity.favoriteTeamID = request.favoriteTeamID
        entity.selectedSeason = request.selectedSeason ?: entity.selectedSeason
        return toDto(repository.save(entity))
    }

    private fun toDto(entity: PreferenceEntity): PreferenceDto =
        PreferenceDto(entity.deviceID, entity.favoriteTeamID, entity.selectedSeason, entity.createdAt.toString(), entity.updatedAt.toString())
}
