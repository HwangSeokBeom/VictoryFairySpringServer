package com.victoryfairy.server.kbo.collector

import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.config.AppProperties
import com.victoryfairy.server.kbo.KboGameService
import com.victoryfairy.server.kbo.KboScrapedDevUpdateSummary
import com.victoryfairy.server.kbo.scheduler.KboScrapedDevUpdateStateService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class KboCollectService(
    private val properties: AppProperties,
    private val pageClient: KboSchedulePageClient,
    private val parser: KboScheduleParser,
    private val gameService: KboGameService,
    private val stateService: KboScrapedDevUpdateStateService,
) {
    @Transactional
    fun collectAndRecordState(season: Int = properties.kbo.scrapedDev.season, seriesType: String? = KboSeriesType.REGULAR_SEASON.name): KboCollectResultDto {
        stateService.markStarted()
        return try {
            val result = collect(season, seriesType)
            stateService.markSuccess(result)
            result
        } catch (error: Exception) {
            stateService.markFailed(error.message ?: "KBO scraped-dev internal collection failed.")
            throw error
        }
    }

    @Transactional
    fun collect(season: Int, seriesType: String?): KboCollectResultDto {
        if (season !in 1900..2100) throw ApiException("VALIDATION_ERROR", "시즌 값은 1900부터 2100 사이여야 합니다.", 400)
        val parsedSeriesType = try {
            KboSeriesType.parse(seriesType)
        } catch (error: IllegalArgumentException) {
            throw ApiException("VALIDATION_ERROR", error.message ?: "seriesType 값을 확인해 주세요.", 400)
        }

        val warnings = mutableListOf<String>()
        val collectedGames = mutableListOf<KboCollectedGameDto>()
        var skipped = 0

        for (month in 1..12) {
            val parseResult = runCatching {
                parser.parse(pageClient.fetchScheduleTableHtml(season, month, parsedSeriesType), season, parsedSeriesType)
            }.getOrElse { error ->
                warnings += "${month}월 KBO 일정 수집에 실패했습니다: ${error.message ?: error::class.simpleName}"
                return@getOrElse KboScheduleParseResult(emptyList(), 0, emptyList())
            }
            collectedGames += parseResult.games
            skipped += parseResult.skipped
            warnings += parseResult.warnings
        }

        var inserted = 0
        var updated = 0
        collectedGames.forEach { game ->
            if (gameService.upsert(game.toNormalized()).inserted) inserted += 1 else updated += 1
        }

        return KboScrapedDevUpdateSummary(
            collectedCount = collectedGames.size,
            inserted = inserted,
            updated = updated,
            skipped = skipped,
            warnings = warnings.distinct(),
            statusCounts = collectedGames.groupingBy { it.status }.eachCount().withDefaultStatuses(),
        )
    }

    private fun Map<String, Int>.withDefaultStatuses(): Map<String, Int> =
        linkedMapOf(
            "final" to (this["final"] ?: 0),
            "scheduled" to (this["scheduled"] ?: 0),
            "canceled" to (this["canceled"] ?: 0),
            "postponed" to (this["postponed"] ?: 0),
        )
}
