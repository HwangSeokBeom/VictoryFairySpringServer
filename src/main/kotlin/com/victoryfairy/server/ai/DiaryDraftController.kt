package com.victoryfairy.server.ai

import com.victoryfairy.server.common.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class DiaryDraftController(private val service: DiaryDraftService) {
    @PostMapping("/api/v1/ai/diary-draft")
    fun draft(@RequestBody body: Map<String, Any?>): ApiResponse<Map<String, Any?>> = ApiResponse.ok(service.createDraft(body))
}
