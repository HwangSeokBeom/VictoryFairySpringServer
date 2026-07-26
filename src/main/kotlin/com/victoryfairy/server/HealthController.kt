package com.victoryfairy.server

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.common.ApiError
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    private val databaseReadinessProbe: DatabaseReadinessProbe,
) {
    @GetMapping("/health")
    fun health(): ApiResponse<Map<String, String>> = ApiResponse.ok(mapOf("status" to "ok"))

    @GetMapping("/ready")
    fun ready(): ResponseEntity<ApiResponse<Map<String, String>>> =
        if (databaseReadinessProbe.isReady()) {
            ResponseEntity.ok(ApiResponse.ok(mapOf("status" to "ready", "database" to "up")))
        } else {
            ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                    ApiResponse<Map<String, String>>(
                        success = false,
                        error = ApiError("NOT_READY", "Required dependencies are unavailable."),
                    ),
                )
        }
}

@Component
class DatabaseReadinessProbe(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun isReady(): Boolean =
        runCatching {
            jdbcTemplate.queryForObject("SELECT 1", Int::class.java) == 1
        }.getOrDefault(false)
}
