package com.victoryfairy.server.ai

data class DiaryDraftRequest(
    val gameDate: String? = null,
    val favoriteTeamName: String? = null,
    val opponentTeamName: String? = null,
    val stadiumName: String? = null,
    val result: String? = null,
    val scoreText: String? = null,
    val moodTags: List<String> = emptyList(),
    val highlightTags: List<String> = emptyList(),
    val companionType: String? = null,
    val tone: String? = null,
    val extraNoteSanitized: String? = null,
    val extraNote: String? = null,
    val locale: String? = null,
)

data class DiaryDraftData(
    val draftText: String,
    val summaryText: String,
    val shareText: String,
    val hashtags: List<String>,
    val model: String? = null,
    val source: String,
    val safetyNotice: String,
)

data class GroqDraftResult(
    val content: String,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
)
