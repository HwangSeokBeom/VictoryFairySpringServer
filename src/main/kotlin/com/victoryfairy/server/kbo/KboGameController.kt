package com.victoryfairy.server.kbo

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.config.AppProperties
import com.victoryfairy.server.kbo.collector.KboDevEndpointGuard
import java.time.Clock
import java.time.LocalDate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class KboGameController(
    private val kboGameService: KboGameService,
    private val properties: AppProperties,
    private val clock: Clock,
    private val guard: KboDevEndpointGuard,
) {
    @GetMapping("/api/v1/kbo/games")
    fun games(@RequestParam(required = false) date: LocalDate?, @RequestParam(required = false) teamID: String?): ApiResponse<KboGamesData> =
        ApiResponse.ok(kboGameService.listGames(date ?: LocalDate.now(clock), teamID))

    @GetMapping("/api/v1/kbo/standings")
    fun standings(@RequestParam(required = false) season: Int?): ApiResponse<KboStandingsData> =
        ApiResponse.ok(kboGameService.standings(season ?: properties.kbo.refresh.season))

    @PostMapping("/api/v1/dev/kbo/seed-sample-game")
    fun seedSampleGame(
        @RequestHeader("X-Admin-Token", required = false) adminToken: String?,
    ): ApiResponse<KboGameResponseItem> {
        guard.assertAllowed(adminToken)
        return ApiResponse.ok(kboGameService.seedSampleGame())
    }
}
