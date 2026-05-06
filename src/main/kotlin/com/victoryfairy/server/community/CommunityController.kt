package com.victoryfairy.server.community

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.device.DeviceIdentityFilter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/community/posts")
class CommunityController(
    private val service: CommunityService,
    private val deviceIdentityFilter: DeviceIdentityFilter,
) {
    @GetMapping
    fun posts(request: HttpServletRequest): ApiResponse<CommunityPostsData> =
        ApiResponse.ok(service.list(deviceIdentityFilter.optionalDeviceID(request)))

    @PostMapping
    fun create(request: HttpServletRequest, @RequestBody body: CommunityPostRequest): ApiResponse<CommunityPostResponse> =
        ApiResponse.ok(service.create(deviceIdentityFilter.requireDeviceID(request), body))

    @PostMapping("/{id}/report")
    fun report(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody(required = false) body: CommunityReportRequest?,
    ): ApiResponse<CommunityPostResponse> =
        ApiResponse.ok(service.report(deviceIdentityFilter.requireDeviceID(request), id, body))
}

@RestController
@RequestMapping("/api/v1/community")
class CommunityUserController(
    private val service: CommunityService,
    private val deviceIdentityFilter: DeviceIdentityFilter,
) {
    @PostMapping("/users/{authorId}/block")
    fun block(request: HttpServletRequest, @PathVariable authorId: String): ApiResponse<CommunityBlockData> =
        ApiResponse.ok(service.block(deviceIdentityFilter.requireDeviceID(request), authorId))

    @DeleteMapping("/users/{authorId}/block")
    fun unblock(request: HttpServletRequest, @PathVariable authorId: String): ApiResponse<CommunityBlockData> =
        ApiResponse.ok(service.unblock(deviceIdentityFilter.requireDeviceID(request), authorId))

    @GetMapping("/blocked-users")
    fun blockedUsers(request: HttpServletRequest): ApiResponse<CommunityBlockedUsersData> =
        ApiResponse.ok(service.blockedUsers(deviceIdentityFilter.requireDeviceID(request)))
}
