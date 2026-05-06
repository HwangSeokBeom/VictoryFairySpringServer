package com.victoryfairy.server.kbo.collector

import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.config.AppProperties
import com.victoryfairy.server.kbo.importer.KboScrapedDevImportService
import com.victoryfairy.server.kbo.scheduler.KboScrapedDevScheduler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class KboCollectController(
    private val collectService: KboCollectService,
    private val importService: KboScrapedDevImportService,
    private val scheduler: KboScrapedDevScheduler,
    private val guard: KboDevEndpointGuard,
    private val properties: AppProperties,
) {
    @PostMapping("/api/v1/dev/kbo/collect-scraped-dev")
    fun collectScrapedDev(
        @RequestBody request: KboCollectRequestDto,
        @RequestHeader("X-Admin-Token", required = false) adminToken: String?,
    ): ApiResponse<KboCollectResultDto> {
        guard.assertAllowed(adminToken)
        return ApiResponse.ok(collectService.collectAndRecordState(request.season, request.seriesType))
    }

    @PostMapping("/api/v1/dev/kbo/update-scraped-dev")
    fun updateScrapedDev(
        @RequestBody(required = false) request: KboScrapedDevUpdateRequestDto?,
        @RequestHeader("X-Admin-Token", required = false) adminToken: String?,
    ): ApiResponse<KboCollectResultDto> {
        guard.assertAllowed(adminToken)
        val body = request ?: KboScrapedDevUpdateRequestDto()
        return when (body.mode.ifBlank { "internal-collector" }) {
            "internal-collector" -> ApiResponse.ok(collectService.collectAndRecordState(body.season ?: properties.kbo.scrapedDev.season, body.seriesType))
            "json-import" -> ApiResponse.ok(importService.importFromConfiguredJsonAndRecordState())
            else -> throw ApiException("VALIDATION_ERROR", "mode는 internal-collector 또는 json-import 여야 합니다.", 400)
        }
    }

    @PostMapping("/api/v1/dev/kbo/import-scraped-dev-json")
    fun importScrapedDevJson(
        @RequestHeader("X-Admin-Token", required = false) adminToken: String?,
    ): ApiResponse<KboCollectResultDto> {
        guard.assertAllowed(adminToken)
        return ApiResponse.ok(importService.importFromConfiguredJsonAndRecordState())
    }

    @GetMapping("/api/v1/dev/kbo/update-scraped-dev/status")
    fun updateStatus(
        @RequestHeader("X-Admin-Token", required = false) adminToken: String?,
    ): ApiResponse<KboScrapedDevScheduler.StatusResponse> {
        guard.assertAllowed(adminToken)
        return ApiResponse.ok(scheduler.status())
    }
}
