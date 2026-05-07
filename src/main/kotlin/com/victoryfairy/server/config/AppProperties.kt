package com.victoryfairy.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(AppProperties::class)
class AppPropertiesConfig

@ConfigurationProperties(prefix = "victory-fairy")
data class AppProperties(
    val publicBaseUrl: String = "http://localhost:8081",
    val cors: CorsProperties = CorsProperties(),
    val kbo: KboProperties = KboProperties(),
    val ai: AiProperties = AiProperties(),
    val profileImage: ProfileImageProperties = ProfileImageProperties(),
    val community: CommunityProperties = CommunityProperties(),
    val legal: LegalProperties = LegalProperties(),
    val news: NewsProperties = NewsProperties(),
) {
    data class CorsProperties(
        val allowedOriginPatterns: String = "*",
    )

    data class KboProperties(
        val scrapedDev: ScrapedDevProperties = ScrapedDevProperties(),
        val refresh: RefreshProperties = RefreshProperties(),
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

    data class RefreshProperties(
        val enabled: Boolean = false,
        val cron: String = "0 0 3,9,15,21 * * *",
        val season: Int = 2026,
        val adminToken: String = "",
        val timeoutSeconds: Long = 180,
        val lockEnabled: Boolean = true,
    )

    data class AiProperties(
        val diaryEnabled: Boolean = false,
        val matchOutlookEnabled: Boolean = false,
        val groqApiKey: String = "",
        val groqModel: String = "llama-3.1-8b-instant",
        val dailyLimit: Int = 10,
        val timeoutSeconds: Long = 12,
    )

    data class ProfileImageProperties(
        val uploadEnabled: Boolean = true,
        val maxBytes: Long = 2_097_152,
        val maxSide: Int = 512,
        val uploadDir: String = "data/uploads/profile",
    )

    data class CommunityProperties(
        val enabled: Boolean = true,
        val postsRequireProfile: Boolean = true,
        val blockEnabled: Boolean = true,
        val policyUrl: String = "https://hwangseokbeom.github.io/VictoryFairy-legal/community-policy.html",
    )

    data class LegalProperties(
        val appHomepageUrl: String = "https://hwangseokbeom.github.io/VictoryFairy-legal/",
        val termsUrl: String = "https://hwangseokbeom.github.io/VictoryFairy-legal/terms.html",
        val privacyPolicyUrl: String = "https://hwangseokbeom.github.io/VictoryFairy-legal/privacy.html",
        val supportUrl: String = "https://hwangseokbeom.github.io/VictoryFairy-legal/support.html",
        val accountDeletionUrl: String = "https://hwangseokbeom.github.io/VictoryFairy-legal/delete-account.html",
        val disclaimerUrl: String = "https://hwangseokbeom.github.io/VictoryFairy-legal/disclaimer.html",
        val communityPolicyUrl: String = "https://hwangseokbeom.github.io/VictoryFairy-legal/community-policy.html",
    )

    data class NewsProperties(
        val provider: String = "local",
        val naverClientId: String = "",
        val naverClientSecret: String = "",
        val naverNewsBaseUrl: String = "https://openapi.naver.com/v1/search/news.json",
        val cacheTtlSeconds: Long = 1800,
    )
}
