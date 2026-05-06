package com.victoryfairy.server.kbo.scheduler

data class KboScrapedDevUpdateState(
    val lastStartedAt: String? = null,
    val lastFinishedAt: String? = null,
    val lastSuccessAt: String? = null,
    val lastStatus: String? = null,
    val lastError: String? = null,
    val lastSummary: Any? = null,
)
