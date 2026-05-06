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
    val community: CommunityProperties = CommunityProperties(),
    val news: NewsProperties = NewsProperties(),
) {
    data class KboProperties(
        val scrapedDev: ScrapedDevProperties = ScrapedDevProperties(),
        val sourceLabelMode: String = "dev",
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
        val dailyLimit: Int = 10,
        val timeoutSeconds: Long = 12,
    )

    data class CommunityProperties(
        val enabled: Boolean = false,
        val policyUrl: String = "https://hwangseokbeom.github.io/VictoryFairy-legal/community-policy.html",
    )

    data class NewsProperties(
        val provider: String = "local",
        val naverClientId: String = "",
        val naverClientSecret: String = "",
        val naverNewsBaseUrl: String = "https://openapi.naver.com/v1/search/news.json",
        val cacheTtlSeconds: Long = 1800,
    )
}
