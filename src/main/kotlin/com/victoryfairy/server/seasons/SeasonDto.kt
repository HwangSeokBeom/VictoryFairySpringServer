package com.victoryfairy.server.seasons

data class SeasonsData(
    val currentSeason: Int,
    val items: List<SeasonItem>,
)

data class SeasonItem(
    val season: Int,
    val label: String,
    val hasRecords: Boolean,
)
