package com.victoryfairy.server.ticket

import com.victoryfairy.server.common.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class TicketOcrController(private val service: TicketOcrParserService) {
    @PostMapping("/api/v1/ticket/parse-ocr-text")
    fun parse(@RequestBody request: TicketOcrParseRequest): ApiResponse<TicketOcrParseResponse> =
        ApiResponse.ok(service.parse(request))
}
