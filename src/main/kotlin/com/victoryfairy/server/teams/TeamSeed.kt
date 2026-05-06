package com.victoryfairy.server.teams

object TeamSeed {
    val teams = listOf(
        TeamDto("lg-twins", "LG 트윈스", "LG", "서울", "#C30452", "#000000", "잠실야구장"),
        TeamDto("doosan-bears", "두산 베어스", "두산", "서울", "#131230", "#FFFFFF", "잠실야구장"),
        TeamDto("kiwoom-heroes", "키움 히어로즈", "키움", "서울", "#570514", "#B07F4A", "고척스카이돔"),
        TeamDto("ssg-landers", "SSG 랜더스", "SSG", "인천", "#CE0E2D", "#FFB81C", "인천 SSG 랜더스필드"),
        TeamDto("kt-wiz", "KT 위즈", "KT", "수원", "#000000", "#ED1C24", "수원 kt wiz 파크"),
        TeamDto("hanwha-eagles", "한화 이글스", "한화", "대전", "#F37321", "#000000", "대전 한화생명 볼파크"),
        TeamDto("samsung-lions", "삼성 라이온즈", "삼성", "대구", "#074CA1", "#FFFFFF", "대구 삼성 라이온즈 파크"),
        TeamDto("kia-tigers", "KIA 타이거즈", "KIA", "광주", "#EA0029", "#061A40", "광주-기아 챔피언스 필드"),
        TeamDto("lotte-giants", "롯데 자이언츠", "롯데", "부산", "#041E42", "#D00F31", "사직야구장"),
        TeamDto("nc-dinos", "NC 다이노스", "NC", "창원", "#315288", "#AF917B", "창원NC파크"),
    )
    val teamIDs = teams.map { it.id }.toSet()
    private val byID = teams.associateBy { it.id }
    private val byName = teams.flatMap { team ->
        listOf(team.name to team.id, team.shortName to team.id)
    }.toMap()

    fun find(id: String): TeamDto? = byID[id]
    fun require(id: String): TeamDto = byID[id] ?: error("unknown team: $id")
    fun idByName(value: String): String? {
        val normalized = value.trim().replace(Regex("\\s+"), " ").replace(Regex("[A-Za-z]+")) { it.value.uppercase() }
        return byName[if (normalized == "KT WIZ") "KT 위즈" else normalized]
    }
}
