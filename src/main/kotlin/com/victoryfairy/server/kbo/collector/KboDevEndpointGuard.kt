package com.victoryfairy.server.kbo.collector

import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.config.AppProperties
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class KboDevEndpointGuard(
    private val properties: AppProperties,
    private val environment: Environment,
) {
    fun assertAllowed(adminToken: String?) {
        if (!properties.kbo.scrapedDev.enabled) {
            throw ApiException("KBO_SCRAPED_DEV_DISABLED", "KBO scraped-dev 기능이 비활성화되어 있습니다.", 403)
        }
        if (isProductionLike()) {
            throw ApiException("KBO_SCRAPED_DEV_FORBIDDEN", "KBO scraped-dev 기능은 production 환경에서 사용할 수 없습니다.", 403)
        }
        val expectedToken = properties.kbo.scrapedDev.adminImportToken.trim()
        if (expectedToken.isNotEmpty() && adminToken != expectedToken) {
            throw ApiException("ADMIN_TOKEN_REQUIRED", "X-Admin-Token 헤더를 확인해 주세요.", 403)
        }
    }

    fun isProductionLike(): Boolean {
        val activeProfiles = environment.activeProfiles.map { it.lowercase() }.toSet()
        if ("test" in activeProfiles) return false
        return activeProfiles.any { it == "prod" || it == "production" } ||
            System.getenv("NODE_ENV").equals("production", ignoreCase = true)
    }
}
