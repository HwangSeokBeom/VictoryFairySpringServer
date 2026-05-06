package com.victoryfairy.server.kbo.importer

object KboStadiumMapper {
    private val aliases = mapOf(
        "잠실" to "잠실야구장", "잠실야구장" to "잠실야구장",
        "고척" to "고척스카이돔", "고척스카이돔" to "고척스카이돔",
        "문학" to "인천 SSG 랜더스필드", "인천 SSG 랜더스필드" to "인천 SSG 랜더스필드",
        "수원" to "수원 kt wiz 파크", "수원 kt wiz 파크" to "수원 kt wiz 파크",
        "대전" to "대전 한화생명 볼파크", "대전 한화생명 볼파크" to "대전 한화생명 볼파크",
        "대구" to "대구 삼성 라이온즈 파크", "대구 삼성 라이온즈 파크" to "대구 삼성 라이온즈 파크",
        "광주" to "광주-기아 챔피언스 필드", "광주-기아 챔피언스 필드" to "광주-기아 챔피언스 필드",
        "사직" to "사직야구장", "사직야구장" to "사직야구장",
        "창원" to "창원NC파크", "창원NC파크" to "창원NC파크",
        "포항" to "포항야구장", "포항야구장" to "포항야구장",
        "울산" to "울산문수야구장", "울산문수야구장" to "울산문수야구장",
        "청주" to "청주야구장", "청주야구장" to "청주야구장",
        "군산" to "군산월명야구장", "군산월명야구장" to "군산월명야구장",
        "이천(두산)" to "이천베어스파크", "이천" to "이천베어스파크",
        "마산" to "마산야구장", "마산야구장" to "마산야구장",
    )

    fun map(value: String?, warnings: MutableList<String>): String? {
        val normalized = value?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        val mapped = aliases[normalized]
        if (mapped == null) warnings += "알 수 없는 구장 '$normalized'은 원문 그대로 저장했습니다."
        return mapped ?: normalized
    }
}
