package com.victoryfairy.server.teams

data class TeamDto(
    val id: String,
    val name: String,
    val shortName: String,
    val city: String,
    val primaryColorHex: String,
    val secondaryColorHex: String?,
    val homeStadiumName: String,
    val active: Boolean = true,
)
