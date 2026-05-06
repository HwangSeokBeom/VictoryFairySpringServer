package com.victoryfairy.server.kbo.importer

import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.config.AppProperties
import com.victoryfairy.server.kbo.KboGameService
import com.victoryfairy.server.kbo.KboScrapedDevUpdateSummary
import com.victoryfairy.server.kbo.scheduler.KboScrapedDevUpdateStateService
import jakarta.transaction.Transactional
import java.nio.file.Files
import java.nio.file.Path
import org.springframework.stereotype.Service

@Service
class KboScrapedDevImportService(
    private val properties: AppProperties,
    private val normalizer: KboScraperJsonNormalizer,
    private val gameService: KboGameService,
    private val stateService: KboScrapedDevUpdateStateService,
) {
    @Transactional
    fun importFromConfiguredJsonAndRecordState(): KboScrapedDevUpdateSummary {
        stateService.markStarted()
        return try {
            val summary = importFromConfiguredJson()
            stateService.markSuccess(summary)
            summary
        } catch (error: Exception) {
            stateService.markFailed(error.message ?: "KBO scraped-dev update failed.")
            throw error
        }
    }

    @Transactional
    fun importFromConfiguredJson(): KboScrapedDevUpdateSummary {
        val path = Path.of(properties.kbo.scrapedDev.inputJson)
        if (!Files.exists(path)) {
            throw ApiException("KBO_SCRAPED_DEV_FILE_NOT_FOUND", "KBO scraped-dev JSON 파일을 찾을 수 없습니다: $path", 400)
        }
        val normalized = normalizer.normalize(Files.readString(path), properties.kbo.scrapedDev.season)
        var inserted = 0
        var updated = 0
        normalized.games.forEach {
            if (gameService.upsert(it).inserted) inserted += 1 else updated += 1
        }
        return KboScrapedDevUpdateSummary(
            collectedCount = normalized.totalRows,
            inserted = inserted,
            updated = updated,
            skipped = normalized.skipped,
            warnings = normalized.warnings,
            statusCounts = normalized.statusCounts,
        )
    }
}
