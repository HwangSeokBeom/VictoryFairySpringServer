package com.victoryfairy.server.profile

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.device.DeviceIdentityFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/me/profile")
class UserProfileController(
    private val service: UserProfileService,
    private val deviceIdentityFilter: DeviceIdentityFilter,
) {
    @GetMapping
    fun get(request: HttpServletRequest): ApiResponse<UserProfileData> =
        ApiResponse.ok(service.get(deviceIdentityFilter.requireDeviceID(request)))

    @PostMapping
    fun post(request: HttpServletRequest, @RequestBody body: UserProfileRequest): ApiResponse<UserProfileData> =
        ApiResponse.ok(service.upsert(deviceIdentityFilter.requireDeviceID(request), body))

    @PutMapping
    fun put(request: HttpServletRequest, @RequestBody body: UserProfileRequest): ApiResponse<UserProfileData> =
        ApiResponse.ok(service.upsert(deviceIdentityFilter.requireDeviceID(request), body))

    @PostMapping("/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadImage(
        request: HttpServletRequest,
        @RequestParam("image") image: MultipartFile,
    ): ApiResponse<ProfileImageUploadData> =
        ApiResponse.ok(service.uploadImage(deviceIdentityFilter.requireDeviceID(request), image))

    @DeleteMapping("/image")
    fun deleteImage(request: HttpServletRequest): ApiResponse<ProfileImageDeleteData> =
        ApiResponse.ok(service.deleteImage(deviceIdentityFilter.requireDeviceID(request)))
}
