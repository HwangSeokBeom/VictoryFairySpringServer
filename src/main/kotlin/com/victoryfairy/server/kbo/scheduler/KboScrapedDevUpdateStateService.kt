package com.victoryfairy.server.kbo.scheduler

import com.fasterxml.jackson.databind.ObjectMapper
import com.victoryfairy.server.config.AppProperties
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class KboScrapedDevUpdateStateService(
    private val properties: AppProperties,
    private val objectMapper: ObjectMapper,
) {
    fun read(): KboScrapedDevUpdateState? {
        val path = path()
        if (!Files.exists(path)) return null
        return runCatching { objectMapper.readValue(path.toFile(), KboScrapedDevUpdateState::class.java) }.getOrNull()
    }

    fun markStarted() {
        write((read() ?: KboScrapedDevUpdateState()).copy(lastStartedAt = Instant.now().toString(), lastStatus = "failed", lastError = null))
    }

    fun markSuccess(summary: Any) {
        val now = Instant.now().toString()
        val previous = read() ?: KboScrapedDevUpdateState()
        write(previous.copy(lastFinishedAt = now, lastSuccessAt = now, lastStatus = "success", lastError = null, lastSummary = summary))
    }

    fun markFailed(message: String) {
        val previous = read() ?: KboScrapedDevUpdateState()
        write(previous.copy(lastFinishedAt = Instant.now().toString(), lastStatus = "failed", lastError = message))
    }

    private fun write(state: KboScrapedDevUpdateState) {
        val path = path()
        Files.createDirectories(path.parent)
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), state)
    }

    private fun path(): Path = Path.of(properties.kbo.scrapedDev.statePath)
}
