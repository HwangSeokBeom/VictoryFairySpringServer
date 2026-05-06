package com.victoryfairy.server.ticket

import com.victoryfairy.server.common.ApiException
import java.text.Normalizer
import java.time.LocalDate
import org.springframework.stereotype.Service

@Service
class TicketOcrParserService {
    fun parse(request: TicketOcrParseRequest): TicketOcrParseResponse {
        val raw = request.ocrText?.takeIf { it.isNotBlank() } ?: throw ApiException("VALIDATION_ERROR", "ocrText 값이 필요합니다.")
        if (raw.length > 4_000) throw ApiException("VALIDATION_ERROR", "ocrText는 4000자 이하여야 합니다.")
        val normalized = normalize(raw)
        val date = findDate(normalized)
        val teamMatches = findTeams(normalized)
        val stadium = findStadium(normalized)
        val seat = findSeat(normalized)
        val warnings = mutableListOf<String>()
        if (date == null) warnings += "DATE_NOT_FOUND"
        if (teamMatches.size > 2) warnings += "TEAM_AMBIGUOUS"
        if (stadium == null) warnings += "STADIUM_NOT_FOUND"
        if (seat == null) warnings += "SEAT_LOW_CONFIDENCE"

        val ordered = orderTeams(normalized, teamMatches.take(2))
        if (ordered.size == 2 && !hasHomeAwaySignal(normalized)) warnings += "TEAM_ORDER_UNCERTAIN"
        if (ordered.isEmpty()) warnings += "TEAM_AMBIGUOUS"

        val confidence = confidence(date, ordered, stadium, seat, warnings)
        return TicketOcrParseResponse(
            candidates = listOf(
                TicketOcrCandidate(
                    confidence = confidence,
                    date = date,
                    homeTeamID = ordered.getOrNull(0),
                    awayTeamID = ordered.getOrNull(1),
                    favoriteTeamID = null,
                    opponentTeamID = null,
                    stadiumName = stadium,
                    seatText = seat,
                    rawMatchedText = normalized.take(500),
                    warnings = warnings.distinct(),
                ),
            ),
        )
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace(Regex("[\\u0000-\\u001F\\u007F]+"), "\n")
            .replace('：', ':')
            .replace('－', '-')
            .replace('–', '-')
            .replace('—', '-')
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{2,}"), "\n")
            .trim()

    private fun findDate(text: String): String? {
        val dateText = normalizeOcrDigitsInNumericContext(text)
        Regex("(20\\d{2})[.\\-/년 ]+\\s*(\\d{1,2})[.\\-/월 ]+\\s*(\\d{1,2})").find(dateText)?.let {
            return toDate(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }
        Regex("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일").find(dateText)?.let {
            return toDate(2026, it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        return null
    }

    private fun toDate(year: Int, month: Int, day: Int): String? =
        runCatching { LocalDate.of(year, month, day).toString() }.getOrNull()

    private fun findTeams(text: String): List<String> =
        teamAliases.entries
            .filter { (alias, _) -> Regex("(?i)(^|[^A-Za-z가-힣])${Regex.escape(alias)}([^A-Za-z가-힣]|$)").containsMatchIn(text) }
            .sortedBy { text.indexOf(it.key, ignoreCase = true).let { index -> if (index < 0) Int.MAX_VALUE else index } }
            .map { it.value }
            .distinct()

    private fun orderTeams(text: String, teams: List<String>): List<String> {
        if (teams.size != 2) return teams
        val home = Regex("(홈|HOME|Home)\\s*[:：]?\\s*([A-Za-z가-힣 ]{1,20})").find(text)?.groupValues?.getOrNull(2)?.let(::teamFromAlias)
        val away = Regex("(원정|AWAY|Away|VISITOR)\\s*[:：]?\\s*([A-Za-z가-힣 ]{1,20})").find(text)?.groupValues?.getOrNull(2)?.let(::teamFromAlias)
        if (home != null && away != null && home != away) return listOf(home, away)
        return teams
    }

    private fun hasHomeAwaySignal(text: String): Boolean = Regex("홈|원정|HOME|AWAY|VISITOR", RegexOption.IGNORE_CASE).containsMatchIn(text)

    private fun teamFromAlias(value: String): String? {
        val compact = value.trim().replace(Regex("\\s+"), " ")
        return teamAliases.entries.firstOrNull { compact.contains(it.key, ignoreCase = true) }?.value
    }

    private fun findStadium(text: String): String? =
        stadiumAliases.entries
            .filter { (alias, _) -> text.contains(alias, ignoreCase = true) }
            .maxByOrNull { it.key.length }
            ?.value

    private fun findSeat(text: String): String? {
        val compact = normalizeOcrDigitsInSeatContext(text).replace(Regex("\\s+"), " ")
        val patterns = listOf(
            Regex("((?:1루|3루|내야|외야)?\\s*\\d{1,4}\\s*(?:블록|구역)\\s*\\d{1,3}\\s*열\\s*\\d{1,4}\\s*번)"),
            Regex("((?:네이비석|테이블석|응원석|중앙석|외야|내야|1루|3루)[^\\n]{0,40}?(?:\\d{1,3}\\s*열\\s*\\d{1,4}\\s*번|좌석\\s*\\d{1,4}))"),
            Regex("((?:좌석|Seat)\\s*[:：]?\\s*[A-Za-z0-9가-힣 \\-]{2,40})", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(compact)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim()
        }
    }

    private fun normalizeOcrDigitsInNumericContext(text: String): String =
        text.replace(Regex("(?<=\\d)[Oo](?=\\d)"), "0")
            .replace(Regex("(?<=\\d)[IiLl](?=\\d)"), "1")

    private fun normalizeOcrDigitsInSeatContext(text: String): String =
        normalizeOcrDigitsInNumericContext(text)
            .replace(Regex("(?i)(^|\\s)[il](?=\\s*루)")) { "${it.groupValues[1]}1" }

    private fun confidence(date: String?, teams: List<String>, stadium: String?, seat: String?, warnings: List<String>): Double {
        var score = 0.1
        if (date != null) score += 0.22
        if (teams.size >= 2) score += 0.26 else if (teams.size == 1) score += 0.12
        if (stadium != null) score += 0.2
        if (seat != null) score += 0.16
        if ("TEAM_ORDER_UNCERTAIN" in warnings) score -= 0.04
        if ("TEAM_AMBIGUOUS" in warnings) score -= 0.08
        return score.coerceIn(0.05, 0.98).let { kotlin.math.round(it * 100.0) / 100.0 }
    }

    private val teamAliases = linkedMapOf(
        "LG 트윈스" to "lg-twins", "엘지" to "lg-twins", "LG" to "lg-twins",
        "두산 베어스" to "doosan-bears", "두산" to "doosan-bears",
        "키움 히어로즈" to "kiwoom-heroes", "히어로즈" to "kiwoom-heroes", "키움" to "kiwoom-heroes",
        "SSG 랜더스" to "ssg-landers", "랜더스" to "ssg-landers", "SSG" to "ssg-landers",
        "KT 위즈" to "kt-wiz", "케이티" to "kt-wiz", "KT" to "kt-wiz",
        "한화 이글스" to "hanwha-eagles", "한화" to "hanwha-eagles",
        "삼성 라이온즈" to "samsung-lions", "삼성" to "samsung-lions",
        "KIA 타이거즈" to "kia-tigers", "기아" to "kia-tigers", "KIA" to "kia-tigers",
        "롯데 자이언츠" to "lotte-giants", "롯데" to "lotte-giants",
        "NC 다이노스" to "nc-dinos", "엔씨" to "nc-dinos", "NC" to "nc-dinos",
    )

    private val stadiumAliases = linkedMapOf(
        "잠실야구장" to "잠실야구장", "잠실" to "잠실야구장",
        "고척스카이돔" to "고척스카이돔", "고척" to "고척스카이돔",
        "인천 SSG 랜더스필드" to "인천 SSG 랜더스필드", "문학" to "인천 SSG 랜더스필드",
        "수원 kt wiz 파크" to "수원 kt wiz 파크", "수원" to "수원 kt wiz 파크",
        "대전 한화생명 볼파크" to "대전 한화생명 볼파크", "대전" to "대전 한화생명 볼파크",
        "대구 삼성 라이온즈 파크" to "대구 삼성 라이온즈 파크", "대구" to "대구 삼성 라이온즈 파크",
        "광주-기아 챔피언스 필드" to "광주-기아 챔피언스 필드", "광주" to "광주-기아 챔피언스 필드",
        "사직야구장" to "사직야구장", "사직" to "사직야구장",
        "창원NC파크" to "창원NC파크", "창원" to "창원NC파크",
        "포항야구장" to "포항야구장", "포항" to "포항야구장",
        "울산문수야구장" to "울산문수야구장", "울산" to "울산문수야구장",
        "청주야구장" to "청주야구장", "청주" to "청주야구장",
        "군산월명야구장" to "군산월명야구장", "군산" to "군산월명야구장",
        "마산야구장" to "마산야구장", "마산" to "마산야구장",
    )
}
