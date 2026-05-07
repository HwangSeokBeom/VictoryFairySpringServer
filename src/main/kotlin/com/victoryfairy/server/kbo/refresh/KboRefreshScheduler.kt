package com.victoryfairy.server.kbo.refresh

import com.victoryfairy.server.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "victory-fairy.kbo.refresh", name = ["enabled"], havingValue = "true")
class KboRefreshScheduler(
    private val properties: AppProperties,
    private val refreshService: KboRefreshService,
) {
    private val logger = LoggerFactory.getLogger(KboRefreshScheduler::class.java)

    @Scheduled(cron = "\${victory-fairy.kbo.refresh.cron}", zone = "Asia/Seoul")
    fun runScheduledRefresh() {
        val season = properties.kbo.refresh.season
        logger.info("[KBO_REFRESH] started season={} trigger=scheduler", season)
        val result = runCatching { refreshService.refreshSeason(season, "scheduler") }.getOrElse { error ->
            logger.warn("[KBO_REFRESH] failed reason={}", error.message ?: error::class.simpleName)
            return
        }
        if (result.successful) {
            logger.info(
                "[KBO_REFRESH] success collected={} inserted={} updated={}",
                result.collectedCount,
                result.inserted,
                result.updated,
            )
        } else {
            logger.warn("[KBO_REFRESH] failed reason={}", result.failureReason ?: "unknown")
        }
    }
}
