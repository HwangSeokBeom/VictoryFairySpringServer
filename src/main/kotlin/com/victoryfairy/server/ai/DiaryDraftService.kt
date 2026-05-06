package com.victoryfairy.server.ai

import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.config.AppProperties
import org.springframework.stereotype.Service

@Service
class DiaryDraftService(private val properties: AppProperties) {
    fun createDraft(request: Map<String, Any?>): Map<String, Any?> {
        if (!properties.ai.diaryEnabled) {
            throw ApiException("AI_FEATURE_DISABLED", "AI 후기 초안 기능은 아직 비활성화되어 있습니다.", 200)
        }
        if (properties.ai.groqApiKey.isBlank()) {
            throw ApiException("AI_FEATURE_DISABLED", "AI 후기 초안 기능은 아직 비활성화되어 있습니다.", 200)
        }
        return mapOf(
            "draftText" to "AI 후기 초안 기능은 서버에서만 호출되도록 준비 중입니다.",
            "model" to properties.ai.groqModel,
            "safetyNotice" to "제공된 경기 정보만 사용합니다.",
        )
    }
}
