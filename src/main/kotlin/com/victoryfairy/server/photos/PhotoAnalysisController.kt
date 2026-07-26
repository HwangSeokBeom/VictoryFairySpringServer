package com.victoryfairy.server.photos

import com.victoryfairy.server.common.ApiResponse
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PhotoAnalysisController {
    @PostMapping(
        "/api/v1/photos/analyze",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    fun disabled(): ApiResponse<Nothing> =
        ApiResponse.fail(
            code = "PHOTO_ANALYSIS_DISABLED",
            message = "사진 분석 기능은 아직 제공되지 않습니다.",
        )
}
