package com.victoryfairy.server.kbo.importer

import com.victoryfairy.server.teams.TeamSeed

object KboTeamMapper {
    fun map(value: String?): String? = value?.let { TeamSeed.idByName(it) }
    fun slug(teamID: String): String = when (teamID) {
        "lg-twins" -> "lg"
        "doosan-bears" -> "doosan"
        "kiwoom-heroes" -> "kiwoom"
        "ssg-landers" -> "ssg"
        "kt-wiz" -> "kt"
        "hanwha-eagles" -> "hanwha"
        "samsung-lions" -> "samsung"
        "kia-tigers" -> "kia"
        "lotte-giants" -> "lotte"
        "nc-dinos" -> "nc"
        else -> teamID
    }
}
