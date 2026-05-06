package com.victoryfairy.server.config

import com.victoryfairy.server.common.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AppConfigController(private val properties: AppProperties) {
    @GetMapping("/api/v1/app-config")
    fun config(): ApiResponse<AppConfigData> =
        ApiResponse.ok(
            AppConfigData(
                publicBaseURL = properties.publicBaseUrl,
                communityEnabled = properties.community.enabled,
                communityPolicyURL = properties.legal.communityPolicyUrl,
                profileImageUploadEnabled = properties.profileImage.uploadEnabled,
            ),
        )
}

data class AppConfigData(
    val publicBaseURL: String,
    val communityEnabled: Boolean,
    val communityPolicyURL: String,
    val profileImageUploadEnabled: Boolean,
)
