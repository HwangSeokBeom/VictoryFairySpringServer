package com.victoryfairy.server.kbo.scheduler

import com.victoryfairy.server.config.AppProperties
import com.victoryfairy.server.kbo.SCRAPED_DEV_SOURCE
import com.victoryfairy.server.kbo.collector.KboCollectService
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import org.springframework.core.env.Environment
import org.springframework.scheduling.TriggerContext
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component

@Component
class KboScrapedDevScheduler(
    private val properties: AppProperties,
    private val stateService: KboScrapedDevUpdateStateService,
    private val collectService: KboCollectService,
    private val environment: Environment,
) : SchedulingConfigurer {
    private val running = AtomicBoolean(false)

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.addTriggerTask(
            { runIfDue() },
            { context: TriggerContext -> CronTrigger(springCron()).nextExecution(context) },
        )
    }

    fun runIfDue() {
        val config = properties.kbo.scrapedDev
        if (!config.enabled || !config.schedulerEnabled || isProduction()) return
        if (!running.compareAndSet(false, true)) return
        try {
            val lastStarted = stateService.read()?.lastStartedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (lastStarted != null && Duration.between(lastStarted, Instant.now()).toHours() < config.minIntervalHours) return
            collectService.collectAndRecordState(config.season)
        } finally {
            running.set(false)
        }
    }

    fun status(): StatusResponse = StatusResponse(
        enabled = properties.kbo.scrapedDev.enabled && properties.kbo.scrapedDev.schedulerEnabled && !isProduction(),
        cron = properties.kbo.scrapedDev.scheduleCron,
        season = properties.kbo.scrapedDev.season,
        source = SCRAPED_DEV_SOURCE,
        minIntervalHours = properties.kbo.scrapedDev.minIntervalHours,
        running = running.get(),
        lastRun = stateService.read(),
    )

    private fun isProduction(): Boolean =
        environment.activeProfiles.any { it.equals("prod", true) || it.equals("production", true) } ||
            System.getenv("NODE_ENV").equals("production", ignoreCase = true)

    private fun springCron(): String {
        val raw = properties.kbo.scrapedDev.scheduleCron.trim()
        return if (raw.split(Regex("\\s+")).size == 5) "0 $raw" else raw
    }

    data class StatusResponse(
        val enabled: Boolean,
        val cron: String,
        val season: Int,
        val source: String,
        val minIntervalHours: Long,
        val running: Boolean,
        val lastRun: KboScrapedDevUpdateState?,
    )
}
