package com.victoryfairy.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(AppProperties::class)
class AppPropertiesConfig

@ConfigurationProperties(prefix = "victory-fairy")
data class AppProperties(
    val kbo: KboProperties = KboProperties(),
    val ai: AiProperties = AiProperties(),
) {
    data class KboProperties(
        val scrapedDev: ScrapedDevProperties = ScrapedDevProperties(),
    )

    data class ScrapedDevProperties(
        val enabled: Boolean = true,
        val inputJson: String = "/Users/hwangseokbeom/Documents/GitHub/VictoryFairyCoreServer/input/kbo-scraper-2026.json",
        val schedulerEnabled: Boolean = false,
        val scheduleCron: String = "0 23 * * *",
        val season: Int = 2026,
        val minIntervalHours: Long = 20,
        val statePath: String = "data/kbo/kbo_scraped_dev_update_state.json",
        val adminImportToken: String = "",
        val requestDelayMs: Long = 350,
    )

    data class AiProperties(
        val diaryEnabled: Boolean = false,
        val groqApiKey: String = "",
        val groqModel: String = "llama-3.1-8b-instant",
    )
}
