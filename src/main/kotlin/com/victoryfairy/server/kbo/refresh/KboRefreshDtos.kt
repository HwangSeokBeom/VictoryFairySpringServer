package com.victoryfairy.server.kbo.refresh

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

data class KboRefreshRequest(
    val season: Int? = null,
)

data class KboRefreshResult(
    val season: Int,
    val collectedCount: Int,
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
    val warnings: List<String>,
    val statusCounts: Map<String, Int>,
    val startedAt: String,
    val finishedAt: String,
    @field:JsonIgnore
    val successful: Boolean = true,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val failureReason: String? = null,
)
