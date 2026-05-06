package com.victoryfairy.server.profile

import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.teams.TeamSeed
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

@Service
class UserProfileService(
    private val repository: UserProfileRepository,
    private val profileImageStorageService: ProfileImageStorageService,
) {
    fun get(deviceID: String): UserProfileData =
        repository.findByDeviceID(deviceID)?.let(::toData) ?: UserProfileData(exists = false)

    fun findEntity(deviceID: String): UserProfileEntity? = repository.findByDeviceID(deviceID)

    fun findByPublicAuthorID(publicAuthorID: String): UserProfileEntity? = repository.findByPublicAuthorID(publicAuthorID)

    fun findByPublicAuthorIDs(publicAuthorIDs: Collection<String>): List<UserProfileEntity> =
        if (publicAuthorIDs.isEmpty()) emptyList() else repository.findByPublicAuthorIDIn(publicAuthorIDs)

    @Transactional
    fun ensurePublicAuthorID(profile: UserProfileEntity): String {
        val hadAuthorID = !profile.publicAuthorID.isNullOrBlank()
        val authorID = profile.ensurePublicAuthorID()
        if (!hadAuthorID) {
            repository.save(profile)
        }
        return authorID
    }

    @Transactional
    fun upsert(deviceID: String, request: UserProfileRequest): UserProfileData {
        val profile = repository.findByDeviceID(deviceID) ?: UserProfileEntity(deviceID = deviceID)
        applyRequest(profile, request)
        return toData(repository.save(profile))
    }

    @Transactional
    fun uploadImage(deviceID: String, image: MultipartFile): ProfileImageUploadData {
        val profile = repository.findByDeviceID(deviceID)
            ?: throw ApiException("PROFILE_REQUIRED", "프로필을 먼저 만들어 주세요.", 403)

        val stored = profileImageStorageService.store(image, profile.profileImagePath)
        profile.profileImagePath = stored.filename
        profile.profileImageMimeType = stored.mimeType
        profile.profileImageUpdatedAt = Instant.now()
        repository.save(profile)
        return ProfileImageUploadData(profileImageURL = profileImageStorageService.toUrl(stored.filename)!!)
    }

    @Transactional
    fun deleteImage(deviceID: String): ProfileImageDeleteData {
        val profile = repository.findByDeviceID(deviceID)
            ?: throw ApiException("PROFILE_REQUIRED", "프로필을 먼저 만들어 주세요.", 403)

        profileImageStorageService.delete(profile.profileImagePath)
        profile.profileImagePath = null
        profile.profileImageMimeType = null
        profile.profileImageUpdatedAt = null
        repository.save(profile)
        return ProfileImageDeleteData(profileImageURL = null)
    }

    fun profileImageURL(profile: UserProfileEntity?): String? = profileImageStorageService.toUrl(profile?.profileImagePath)

    private fun applyRequest(profile: UserProfileEntity, request: UserProfileRequest) {
        val nickname = request.nickname.trim().replace(Regex("\\s+"), " ")
        validateNickname(nickname)
        val team = TeamSeed.find(request.favoriteTeamID)
            ?: throw ApiException("VALIDATION_ERROR", "존재하지 않는 팀입니다.")
        val profileEmoji = request.profileEmoji?.trim()?.takeIf { it.isNotEmpty() }
        if (profileEmoji != null && profileEmoji.length > 16) {
            throw ApiException("VALIDATION_ERROR", "프로필 이모지는 16자 이하로 입력해 주세요.")
        }

        profile.nickname = nickname
        profile.favoriteTeamID = team.id
        profile.profileEmoji = profileEmoji
    }

    private fun validateNickname(nickname: String) {
        if (!NICKNAME_PATTERN.matches(nickname)) {
            throw ApiException("VALIDATION_ERROR", "닉네임은 한글, 영문, 숫자, 공백만 사용해 2~12자로 입력해 주세요.")
        }
        if (PROHIBITED_NICKNAME_WORDS.any { nickname.contains(it, ignoreCase = true) }) {
            throw ApiException("VALIDATION_ERROR", "사용할 수 없는 닉네임입니다.")
        }
    }

    private fun toData(profile: UserProfileEntity): UserProfileData {
        val team = TeamSeed.find(profile.favoriteTeamID)
        return UserProfileData(
            exists = true,
            nickname = profile.nickname,
            favoriteTeamID = profile.favoriteTeamID,
            favoriteTeamName = team?.name ?: profile.favoriteTeamID,
            profileEmoji = profile.profileEmoji,
            profileImageURL = profileImageStorageService.toUrl(profile.profileImagePath),
            createdAt = profile.createdAt.toString(),
            updatedAt = profile.updatedAt.toString(),
        )
    }

    companion object {
        private val NICKNAME_PATTERN = Regex("^[가-힣A-Za-z0-9 ]{2,12}$")
        private val PROHIBITED_NICKNAME_WORDS = listOf(
            "시발",
            "씨발",
            "병신",
            "새끼",
            "도박",
            "베팅",
            "관리자",
            "운영자",
        )
    }
}
