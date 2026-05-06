package com.victoryfairy.server.ticket

data class TicketOcrParseRequest(
    val ocrText: String? = null,
    val locale: String? = null,
)

data class TicketOcrParseResponse(
    val candidates: List<TicketOcrCandidate>,
    val message: String = "티켓에서 추정한 정보예요. 저장 전 꼭 확인해 주세요.",
)

data class TicketOcrCandidate(
    val confidence: Double,
    val date: String?,
    val homeTeamID: String?,
    val awayTeamID: String?,
    val favoriteTeamID: String?,
    val opponentTeamID: String?,
    val stadiumName: String?,
    val seatText: String?,
    val rawMatchedText: String,
    val warnings: List<String>,
)
