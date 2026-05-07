package com.victoryfairy.server.kbo.refresh

import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.config.AppProperties
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class KboRefreshAdminController(
    private val properties: AppProperties,
    private val refreshService: KboRefreshService,
) {
    @PostMapping("/api/v1/admin/kbo/refresh")
    fun refresh(
        @RequestBody(required = false) request: KboRefreshRequest?,
        @RequestHeader("X-Admin-Token", required = false) adminToken: String?,
    ): ApiResponse<KboRefreshResult> {
        assertAdminToken(adminToken)
        val season = request?.season ?: properties.kbo.refresh.season
        val result = refreshService.refreshSeason(season, "admin")
        if (!result.successful) {
            throw ApiException("KBO_REFRESH_FAILED", result.failureReason ?: "KBO refresh failed.", 503)
        }
        return ApiResponse.ok(result)
    }

    private fun assertAdminToken(adminToken: String?) {
        val token = adminToken?.trim()
        if (token.isNullOrBlank()) {
            throw ApiException("ADMIN_TOKEN_REQUIRED", "X-Admin-Token header is required.", 403)
        }
        val expected = properties.kbo.refresh.adminToken.trim().ifBlank {
            properties.kbo.scrapedDev.adminImportToken.trim()
        }
        if (expected.isBlank()) {
            throw ApiException("ADMIN_TOKEN_REQUIRED", "KBO refresh admin token is not configured.", 403)
        }
        if (token != expected) {
            throw ApiException("ADMIN_TOKEN_INVALID", "X-Admin-Token header is invalid.", 403)
        }
    }
}
