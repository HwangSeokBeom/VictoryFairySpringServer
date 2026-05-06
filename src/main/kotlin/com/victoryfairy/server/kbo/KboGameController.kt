package com.victoryfairy.server.kbo

import com.victoryfairy.server.common.ApiResponse
import java.time.LocalDate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class KboGameController(
    private val kboGameService: KboGameService,
) {
    @GetMapping("/api/v1/kbo/games")
    fun games(@RequestParam date: LocalDate, @RequestParam(required = false) teamID: String?): ApiResponse<KboGamesData> =
        ApiResponse.ok(kboGameService.listGames(date, teamID))

    @GetMapping("/api/v1/kbo/standings")
    fun standings(@RequestParam season: Int): ApiResponse<KboStandingsData> = ApiResponse.ok(kboGameService.standings(season))

    @PostMapping("/api/v1/dev/kbo/seed-sample-game")
    fun seedSampleGame(): ApiResponse<KboGameResponseItem> = ApiResponse.ok(kboGameService.seedSampleGame())
}
