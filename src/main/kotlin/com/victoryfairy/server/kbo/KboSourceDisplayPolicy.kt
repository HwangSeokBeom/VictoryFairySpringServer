package com.victoryfairy.server.kbo

import com.victoryfairy.server.config.AppProperties
import org.springframework.stereotype.Component

const val SCRAPED_DEV_REVIEW_SOURCE_LABEL = "참고용 경기 정보"
const val SCRAPED_DEV_SOURCE_DISCLOSURE = "이 정보는 기록 입력을 돕기 위한 참고용 정보이며, 공식 기록은 KBO 공식 사이트에서 확인해 주세요."

@Component
class KboSourceDisplayPolicy(private val properties: AppProperties) {
    fun label(source: String): String = when (source) {
        SCRAPED_DEV_SOURCE -> if (isProductionSafeMode()) SCRAPED_DEV_REVIEW_SOURCE_LABEL else SCRAPED_DEV_SOURCE_LABEL
        "admin-import", "admin-entry" -> "관리자 입력 데이터"
        "provider" -> "합법 데이터 제공사 데이터"
        "official" -> "공식 허가 데이터"
        else -> "데이터 없음"
    }

    fun disclosure(source: String): String? = when {
        source == SCRAPED_DEV_SOURCE && isProductionSafeMode() -> SCRAPED_DEV_SOURCE_DISCLOSURE
        else -> null
    }

    private fun isProductionSafeMode(): Boolean =
        properties.kbo.sourceLabelMode.trim().lowercase() in setOf("review", "production", "prod", "production-safe")
}
