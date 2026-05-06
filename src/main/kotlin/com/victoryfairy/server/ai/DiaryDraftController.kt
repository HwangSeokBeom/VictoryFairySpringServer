package com.victoryfairy.server.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.victoryfairy.server.common.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class DiaryDraftController(
    private val service: DiaryDraftService,
    private val objectMapper: ObjectMapper,
) {
    @PostMapping("/api/v1/ai/diary-draft")
    fun draft(@RequestBody body: JsonNode, servletRequest: HttpServletRequest): ApiResponse<DiaryDraftData> {
        service.rejectUnsafeJson(body)
        val request = objectMapper.treeToValue(body, DiaryDraftRequest::class.java)
        val key = servletRequest.getHeader("X-Device-ID")?.takeIf { it.isNotBlank() } ?: servletRequest.remoteAddr ?: "unknown"
        return ApiResponse.ok(service.createAiDraft(request, key))
    }

    @PostMapping("/api/v1/diary/template-draft")
    fun templateDraft(@RequestBody body: JsonNode): ApiResponse<DiaryDraftData> {
        service.rejectUnsafeJson(body)
        return ApiResponse.ok(service.createTemplateDraft(objectMapper.treeToValue(body, DiaryDraftRequest::class.java)))
    }
}
