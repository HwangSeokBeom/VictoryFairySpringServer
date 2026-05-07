package com.victoryfairy.server.kbo.refresh

import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.config.AppProperties
import com.victoryfairy.server.kbo.collector.KboCollectService
import com.victoryfairy.server.kbo.collector.KboSeriesType
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import org.springframework.stereotype.Service

@Service
class KboRefreshService(
    private val properties: AppProperties,
    private val collectService: KboCollectService,
) {
    private val running = AtomicBoolean(false)

    fun refreshSeason(season: Int): KboRefreshResult = refreshSeason(season, "manual")

    fun refreshSeason(season: Int, _trigger: String): KboRefreshResult {
        if (season !in 1900..2100) throw ApiException("VALIDATION_ERROR", "시즌 값은 1900부터 2100 사이여야 합니다.", 400)
        val lockAcquired = !properties.kbo.refresh.lockEnabled || running.compareAndSet(false, true)
        if (!lockAcquired) {
            throw ApiException("KBO_REFRESH_IN_PROGRESS", "KBO refresh is already running.", 429)
        }

        val startedAt = Instant.now()
        return try {
            val summary = collectService.collect(season, KboSeriesType.REGULAR_SEASON.name)
            val finishedAt = Instant.now()
            val failureReason = failureReason(summary.warnings, summary.collectedCount)
            KboRefreshResult(
                season = season,
                collectedCount = summary.collectedCount,
                inserted = summary.inserted,
                updated = summary.updated,
                skipped = summary.skipped,
                warnings = summary.warnings,
                statusCounts = summary.statusCounts,
                startedAt = startedAt.toString(),
                finishedAt = finishedAt.toString(),
                successful = failureReason == null,
                failureReason = failureReason,
            )
        } catch (error: ApiException) {
            throw error
        } catch (error: Exception) {
            failureResult(season, startedAt, sanitizeReason(error))
        } finally {
            if (properties.kbo.refresh.lockEnabled) running.set(false)
        }
    }

    fun isRunning(): Boolean = running.get()

    private fun failureReason(warnings: List<String>, collectedCount: Int): String? {
        if (collectedCount > 0) return null
        val collectionFailures = warnings.count { it.contains("수집에 실패") }
        return when {
            collectionFailures >= 12 -> "KBO schedule collection failed for every month."
            warnings.any { it.contains("Playwright", ignoreCase = true) || it.contains("browser", ignoreCase = true) } ->
                "KBO browser collection failed."
            else -> null
        }
    }

    private fun failureResult(season: Int, startedAt: Instant, reason: String): KboRefreshResult =
        KboRefreshResult(
            season = season,
            collectedCount = 0,
            inserted = 0,
            updated = 0,
            skipped = 0,
            warnings = listOf(reason),
            statusCounts = emptyStatusCounts(),
            startedAt = startedAt.toString(),
            finishedAt = Instant.now().toString(),
            successful = false,
            failureReason = reason,
        )

    private fun sanitizeReason(error: Exception): String =
        error.message
            ?.lineSequence()
            ?.firstOrNull()
            ?.take(200)
            ?.ifBlank { null }
            ?: "KBO refresh failed."

    private fun emptyStatusCounts(): Map<String, Int> =
        linkedMapOf(
            "final" to 0,
            "scheduled" to 0,
            "canceled" to 0,
            "postponed" to 0,
        )
}
