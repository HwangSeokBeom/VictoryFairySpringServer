package com.victoryfairy.server.news

object NewsTeamQueryMapper {
    private val teamQueries = mapOf(
        "samsung-lions" to "삼성 라이온즈 야구",
        "kia-tigers" to "KIA 타이거즈 야구",
        "hanwha-eagles" to "한화 이글스 야구",
        "lg-twins" to "LG 트윈스 야구",
        "doosan-bears" to "두산 베어스 야구",
        "lotte-giants" to "롯데 자이언츠 야구",
        "ssg-landers" to "SSG 랜더스 야구",
        "kt-wiz" to "KT 위즈 야구",
        "nc-dinos" to "NC 다이노스 야구",
        "kiwoom-heroes" to "키움 히어로즈 야구",
    )

    fun queryFor(teamID: String?): NewsQuery {
        val normalizedTeamID = teamID?.trim()?.takeIf { it.isNotEmpty() }
        val query = teamQueries[normalizedTeamID]
        return NewsQuery(
            query = query ?: "KBO 야구",
            knownTeamID = normalizedTeamID?.takeIf { query != null },
        )
    }
}

data class NewsQuery(
    val query: String,
    val knownTeamID: String?,
)
