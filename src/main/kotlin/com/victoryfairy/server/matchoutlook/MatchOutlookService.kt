package com.victoryfairy.server.matchoutlook

import com.fasterxml.jackson.databind.ObjectMapper
import com.victoryfairy.server.ai.GroqClient
import com.victoryfairy.server.attendance.AttendanceLogDto
import com.victoryfairy.server.attendance.AttendanceLogService
import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.config.AppProperties
import com.victoryfairy.server.kbo.KboGameService
import com.victoryfairy.server.news.NewsArticleItem
import com.victoryfairy.server.news.NewsService
import com.victoryfairy.server.teams.TeamDto
import com.victoryfairy.server.teams.TeamSeed
import java.text.Normalizer
import java.time.Duration
import java.time.LocalDate
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service

private const val OUTLOOK_SUMMARY_WITH_NEWS = "최근 야구 소식과 내 직관 기록을 바탕으로 오늘 경기를 더 재미있게 볼 포인트를 정리했어요."
private const val OUTLOOK_SUMMARY_WITHOUT_NEWS = "내 직관 기록으로 오늘 경기를 더 재미있게 볼 포인트를 정리했어요."
private const val OUTLOOK_DISCLAIMER = "공식 예측이나 승부 정보가 아닙니다."

@Service
class MatchOutlookService(
    private val attendanceLogService: AttendanceLogService,
    private val newsService: NewsService,
    private val groqClient: GroqClient,
    private val objectMapper: ObjectMapper,
    private val properties: AppProperties,
    private val kboGameService: KboGameService,
    private val environment: Environment,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun create(deviceID: String?, request: MatchOutlookRequest): MatchOutlookData {
        val favoriteTeam = TeamSeed.find(request.favoriteTeamID)
            ?: throw ApiException("VALIDATION_ERROR", "존재하지 않는 팀입니다.")
        val opponentTeam = TeamSeed.find(request.opponentTeamID)
            ?: throw ApiException("VALIDATION_ERROR", "존재하지 않는 팀입니다.")
        if (request.favoriteTeamID == request.opponentTeamID) {
            throw ApiException("VALIDATION_ERROR", "응원팀과 상대팀은 같을 수 없습니다.")
        }

        val date = runCatching { LocalDate.parse(request.date) }
            .getOrElse { throw ApiException("VALIDATION_ERROR", "date는 yyyy-MM-dd 형식이어야 합니다.", 400) }
        val stadiumName = request.stadiumName?.trim()?.takeIf { it.isNotEmpty() }
        val seasonLogs = deviceID
            ?.let { attendanceLogService.list(it, date.year, null) }
            .orEmpty()
            .filter { it.favoriteTeamID == request.favoriteTeamID }
        val opponentLogs = seasonLogs.filter { it.opponentTeamID == request.opponentTeamID }
        val stadiumLogs = stadiumName?.let { stadium -> seasonLogs.filter { it.stadiumName == stadium } }.orEmpty()
        val newsContext = fetchNewsContext(favoriteTeam, opponentTeam)
        val newsReferences = newsContext.map { MatchOutlookNewsReference(it.title, it.sourceName, it.url) }
        val context = OutlookContext(favoriteTeam, opponentTeam, date, stadiumName, seasonLogs, opponentLogs, stadiumLogs, newsContext)

        logConfiguration()
        log.info("[MatchOutlook] newsContext count={}", newsContext.size)

        val aiResult = generateAiOutlook(context)
        val points = aiResult?.points ?: buildTemplatePoints(context)
        val generatedBy = if (aiResult != null) "ai" else "template"
        val fallbackReason = aiResult?.let { null } ?: templateReason()

        if (generatedBy == "template") {
            log.info("[MatchOutlook] generatedBy=template reason={} points={} newsRefs={}", fallbackReason, points.size, newsReferences.size)
        } else {
            log.info("[MatchOutlook] generatedBy=ai points={} newsRefs={}", points.size, newsReferences.size)
        }

        return MatchOutlookData(
            title = "${favoriteTeam.shortName} vs ${opponentTeam.shortName} AI 관전 포인트",
            summary = if (newsReferences.isEmpty()) OUTLOOK_SUMMARY_WITHOUT_NEWS else OUTLOOK_SUMMARY_WITH_NEWS,
            points = points,
            newsReferences = newsReferences,
            confidenceLabel = "재미용",
            generatedBy = generatedBy,
            disclaimer = OUTLOOK_DISCLAIMER,
        )
    }

    private fun logConfiguration() {
        val newsProvider = properties.news.provider.trim().lowercase().ifBlank { "local" }
        log.info(
            "[MatchOutlook] aiEnabled={} groqKeyConfigured={} newsProvider={} naverConfigured={}",
            properties.ai.matchOutlookEnabled,
            properties.ai.groqApiKey.isNotBlank(),
            newsProvider,
            isNaverConfigured(),
        )
    }

    private fun fetchNewsContext(favoriteTeam: TeamDto, opponentTeam: TeamDto): List<NewsArticleItem> {
        if (!isNaverConfigured()) return emptyList()

        val queries = listOf(
            "${favoriteTeam.name} ${opponentTeam.name} 야구",
            "${favoriteTeam.name} 야구",
            "${opponentTeam.name} 야구",
        )
        val fetched = queries.flatMap { query ->
            val items = runCatching {
                newsService.searchByQuery(query, NEWS_PER_QUERY, listOf(favoriteTeam.id, opponentTeam.id)).items
            }.getOrElse {
                emptyList()
            }
            logLocal("[MatchOutlookNews] query=\"{}\" fetched={}", query, items.size)
            items
        }
            .distinctBy { it.url }

        val filtered = filterNewsContext(fetched, favoriteTeam.id, opponentTeam.id)
        logLocal("[MatchOutlookNews] accepted={} rejected={}", filtered.accepted.size, filtered.rejected.size)
        filtered.rejected.forEach { rejected ->
            logLocal("[MatchOutlookNews] rejected title=\"{}\" reason={}", rejected.item.title, rejected.reason)
        }
        return filtered.accepted.take(MAX_NEWS_REFERENCES)
    }

    private fun isNaverConfigured(): Boolean =
        properties.news.provider.trim().equals("naver", ignoreCase = true) &&
            properties.news.naverClientId.isNotBlank() &&
            properties.news.naverClientSecret.isNotBlank()

    private fun filterNewsContext(
        items: List<NewsArticleItem>,
        favoriteTeamID: String,
        opponentTeamID: String,
    ): NewsFilterResult {
        val allowedTeamIDs = setOf(favoriteTeamID, opponentTeamID)
        val allowedKeywords = allowedTeamIDs.flatMap { TEAM_KEYWORDS[it].orEmpty() }
        val unrelatedTeams = TEAM_KEYWORDS.filterKeys { it !in allowedTeamIDs }
        val accepted = mutableListOf<NewsArticleItem>()
        val rejected = mutableListOf<RejectedNewsItem>()

        items.forEach { item ->
            val text = normalizeForMatching("${item.title} ${item.summary.orEmpty()}")
            val hasAllowedTeam = allowedKeywords.any { containsKeyword(text, it) }
            val hasUnrelatedTeam = unrelatedTeams.values.flatten().any { containsKeyword(text, it) }

            when {
                hasAllowedTeam -> accepted += item
                hasUnrelatedTeam -> rejected += RejectedNewsItem(item, "unrelated_team")
                else -> rejected += RejectedNewsItem(item, "no_matchup_team")
            }
        }

        return NewsFilterResult(accepted, rejected)
    }

    private fun generateAiOutlook(context: OutlookContext): AiOutlookResult? {
        if (!properties.ai.matchOutlookEnabled) return null
        if (properties.ai.groqApiKey.isBlank()) return null

        return runCatching {
            val result = groqClient.requestDraft(
                apiKey = properties.ai.groqApiKey,
                model = properties.ai.groqModel.ifBlank { "llama-3.1-8b-instant" },
                prompt = buildPrompt(context),
                timeout = Duration.ofSeconds(properties.ai.timeoutSeconds.coerceIn(3, 15)),
            )
            parseAndValidateAiResult(result.content, context)
        }.getOrElse {
            null
        }
    }

    private fun templateReason(): String = when {
        !properties.ai.matchOutlookEnabled -> "ai_disabled"
        properties.ai.groqApiKey.isBlank() -> "groq_key_missing"
        else -> "ai_failed_or_invalid"
    }

    private fun buildPrompt(context: OutlookContext): String =
        """
        VictoryFairy는 KBO 직관 기록 앱입니다. 오늘 경기 관전 포인트를 한국어 JSON object 하나로만 작성하세요.

        반환 스키마:
        {
          "points": [
            {"title": "최근 분위기", "body": "..."},
            {"title": "내 직관 기록", "body": "..."},
            {"title": "응원 포인트", "body": "..."}
          ]
        }

        정책:
        - 제공된 뉴스 제목/요약, 사용자 직관 기록, KBO 참고 컨텍스트만 사용합니다.
        - 뉴스 URL을 만들거나 기사 본문을 인용하지 않습니다.
        - matchup은 ${context.favoriteTeam.name} vs ${context.opponentTeam.name}입니다.
        - 허용 팀: ${allowedTeamKeywords(context).joinToString(", ")}
        - 관련 없는 팀이나 선수는 제공된 accepted news context에 직접 등장하고 이 matchup과 직접 관련된 경우가 아니면 언급하지 않습니다.
        - news context가 비어 있거나 약하면 "최근 관련 뉴스는 제한적"이라고 말하고, 관련 없는 KBO 뉴스를 사용하지 않습니다.
        - 공식 예측, 베팅, 배당, 확률, 적중률처럼 보이는 표현을 쓰지 않습니다.
        - 승패를 단정하거나 선수 실명과 현장 사건을 새로 만들지 않습니다.
        - unrelatedTeams=${rejectedTeamKeywords(context).joinToString(", ")}
        - 3~5개 points만 작성하고, 각 body는 140자 이하, 재미용 관전 포인트 톤으로 작성합니다.
        - 한국어 JSON object 하나만 반환합니다.

        경기:
        date=${context.date}
        favoriteTeam=${context.favoriteTeam.name}
        opponentTeam=${context.opponentTeam.name}
        stadiumName=${context.stadiumName ?: ""}

        직관 기록:
        ${attendanceContext(context)}

        최근 뉴스 제목/요약:
        ${newsPromptContext(context.news)}

        KBO 참고 컨텍스트:
        ${kboReferenceContext(context)}
        """.trimIndent()

    private fun parseAndValidateAiResult(content: String, context: OutlookContext): AiOutlookResult? {
        val json = extractJsonObject(content) ?: return null
        val node = runCatching { objectMapper.readTree(json) }.getOrNull() ?: return null
        val array = node.path("points").takeIf { it.isArray } ?: return null
        val parsed = array.map { item ->
            MatchOutlookPoint(
                title = sanitizeText(item.path("title").asText("")).take(30),
                body = sanitizeText(item.path("body").asText("")).take(220),
            )
        }
        val points = REQUIRED_POINT_TITLES.map { requiredTitle ->
            parsed.firstOrNull { it.title == requiredTitle } ?: return null
        }
        if (points.any { it.body.isBlank() }) return null
        val text = points.joinToString("\n") { "${it.title}\n${it.body}" }
        if (containsBannedTerms(text)) return null
        findUnrelatedMention(text, context)?.let { term ->
            logLocal("[MatchOutlook] aiRejected reason=unrelated_team_mention term={}", term)
            return null
        }
        return AiOutlookResult(points)
    }

    private fun extractJsonObject(content: String): String? {
        val trimmed = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else null
    }

    private fun buildTemplatePoints(context: OutlookContext): List<MatchOutlookPoint> {
        val recentMood = if (context.news.isEmpty()) {
            "최근 관련 야구 소식은 아직 충분히 찾지 못했어요."
        } else {
            val titles = context.news.take(2).joinToString(", ") { it.title }
            "최근 관련 소식으로는 $titles 등이 있어요. 경기 전에는 헤드라인 중심으로 분위기를 살펴보면 좋아요."
        }

        return listOf(
            MatchOutlookPoint("최근 분위기", recentMood),
            MatchOutlookPoint("내 직관 기록", attendanceTemplateBody(context)),
            MatchOutlookPoint("응원 포인트", cheerTemplateBody(context)),
        )
    }

    private fun attendanceTemplateBody(context: OutlookContext): String {
        if (context.seasonLogs.isEmpty()) {
            return "아직 직관 기록이 적어 개인화된 관전 포인트가 제한적이에요."
        }
        if (context.opponentLogs.isEmpty()) {
            return "내 직관 기록 기준 ${context.opponentTeam.shortName}전 표본은 아직 적어요. 오늘 경기가 상대팀 흐름을 쌓는 기준이 될 수 있어요."
        }
        val counts = resultCounts(context.opponentLogs)
        return "${context.opponentTeam.shortName}전 직관 기록은 ${context.opponentLogs.size}경기, ${counts.summary()}로 남아 있어요."
    }

    private fun cheerTemplateBody(context: OutlookContext): String {
        if (context.stadiumName != null) {
            if (context.stadiumLogs.isEmpty()) {
                return "${context.stadiumName}에서는 아직 기록이 적어요. 기록이 쌓일수록 상대팀, 구장, 최근 흐름 기준으로 더 자세히 볼 수 있어요."
            }
            val counts = resultCounts(context.stadiumLogs)
            return "${context.stadiumName}에서는 ${context.stadiumLogs.size}경기 기록이 있고, ${counts.summary()}였어요."
        }

        val recentLogs = context.seasonLogs.sortedByDescending { it.date }.take(3)
        if (recentLogs.isNotEmpty()) {
            val latest = recentLogs.first()
            val counts = resultCounts(recentLogs)
            return "최근 직관 흐름은 ${latest.result.toKoreanResult()}로 시작해 최근 ${recentLogs.size}경기 기준 ${counts.summary()}예요."
        }

        return "기록이 쌓일수록 상대팀, 구장, 최근 흐름 기준으로 더 자세히 볼 수 있어요."
    }

    private fun attendanceContext(context: OutlookContext): String {
        if (context.seasonLogs.isEmpty()) return "seasonLogs=0"
        return listOf(
            "seasonLogs=${context.seasonLogs.size}",
            "opponentLogs=${context.opponentLogs.size} ${resultCounts(context.opponentLogs).summary()}",
            "stadiumLogs=${context.stadiumLogs.size} ${resultCounts(context.stadiumLogs).summary()}",
            "recent=${context.seasonLogs.sortedByDescending { it.date }.take(3).joinToString { "${it.date}:${it.opponentTeamName}:${it.result}" }}",
        ).joinToString("\n")
    }

    private fun newsPromptContext(news: List<NewsArticleItem>): String {
        val items = news.take(5)
        if (items.isEmpty()) return "available=false"
        return items.mapIndexed { index, item ->
            "${index + 1}. title=${item.title}; source=${item.sourceName}; summary=${item.summary.orEmpty().take(180)}"
        }.joinToString("\n")
    }

    private fun kboReferenceContext(context: OutlookContext): String =
        runCatching {
            val standings = kboGameService.standings(context.date.year)
            val rows = standings.items
                .filter { it.teamID in setOf(context.favoriteTeam.id, context.opponentTeam.id) }
                .joinToString("\n") {
                    "${it.shortName}: rank=${it.rank}, games=${it.games}, record=${it.wins}W-${it.losses}L-${it.draws}D, recent=${it.recentResults}"
                }
            rows.ifBlank { standings.message ?: "available=false" }
        }.getOrDefault("available=false")

    private fun resultCounts(logs: List<AttendanceLogDto>): ResultCounts = ResultCounts(
        wins = logs.count { it.result == "win" },
        losses = logs.count { it.result == "loss" },
        draws = logs.count { it.result == "draw" },
        canceled = logs.count { it.result == "canceled" },
    )

    private fun sanitizeText(value: String?): String {
        if (value == null) return ""
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsBannedTerms(value: String): Boolean =
        BANNED_PATTERNS.any { it.containsMatchIn(value) }

    private fun findUnrelatedMention(value: String, context: OutlookContext): String? {
        val allowedTeamIDs = setOf(context.favoriteTeam.id, context.opponentTeam.id)
        TEAM_KEYWORDS
            .filterKeys { it !in allowedTeamIDs }
            .values
            .flatten()
            .firstOrNull { containsKeyword(normalizeForMatching(value), it) }
            ?.let { return it }

        val acceptedNewsText = normalizeForMatching(context.news.joinToString(" ") { "${it.title} ${it.summary.orEmpty()}" })
        return UNRELATED_PLAYER_KEYWORDS.firstOrNull { playerKeyword ->
            containsKeyword(normalizeForMatching(value), playerKeyword) &&
                !containsKeyword(acceptedNewsText, playerKeyword)
        }
    }

    private fun allowedTeamKeywords(context: OutlookContext): List<String> =
        listOf(context.favoriteTeam.id, context.opponentTeam.id).flatMap { TEAM_KEYWORDS[it].orEmpty() }

    private fun rejectedTeamKeywords(context: OutlookContext): List<String> =
        TEAM_KEYWORDS.filterKeys { it !in setOf(context.favoriteTeam.id, context.opponentTeam.id) }.values.flatten()

    private fun normalizeForMatching(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

    private fun containsKeyword(normalizedText: String, keyword: String): Boolean {
        val normalizedKeyword = normalizeForMatching(keyword)
        if (normalizedKeyword.isBlank()) return false
        val asciiToken = normalizedKeyword.all { it.isLetterOrDigit() && it.code < 128 }
        return if (asciiToken) {
            Regex("(?<![a-z0-9])${Regex.escape(normalizedKeyword)}(?![a-z0-9])").containsMatchIn(normalizedText)
        } else {
            normalizedText.contains(normalizedKeyword)
        }
    }

    private fun logLocal(message: String, vararg args: Any?) {
        if (environment.activeProfiles.any { it.equals("local", ignoreCase = true) }) {
            log.info(message, *args)
        }
    }

    private fun String.toKoreanResult(): String = when (this) {
        "win" -> "승리"
        "loss" -> "패배"
        "draw" -> "무승부"
        "canceled" -> "취소"
        else -> this
    }

    private data class OutlookContext(
        val favoriteTeam: TeamDto,
        val opponentTeam: TeamDto,
        val date: LocalDate,
        val stadiumName: String?,
        val seasonLogs: List<AttendanceLogDto>,
        val opponentLogs: List<AttendanceLogDto>,
        val stadiumLogs: List<AttendanceLogDto>,
        val news: List<NewsArticleItem>,
    )

    private data class AiOutlookResult(val points: List<MatchOutlookPoint>)

    private data class NewsFilterResult(
        val accepted: List<NewsArticleItem>,
        val rejected: List<RejectedNewsItem>,
    )

    private data class RejectedNewsItem(
        val item: NewsArticleItem,
        val reason: String,
    )

    private data class ResultCounts(
        val wins: Int,
        val losses: Int,
        val draws: Int,
        val canceled: Int,
    ) {
        fun summary(): String {
            val parts = mutableListOf<String>()
            if (wins > 0) parts += "${wins}승"
            if (losses > 0) parts += "${losses}패"
            if (draws > 0) parts += "${draws}무"
            if (canceled > 0) parts += "${canceled}취소"
            return parts.ifEmpty { listOf("결과 기록 없음") }.joinToString(" ")
        }
    }

    companion object {
        private val REQUIRED_POINT_TITLES = listOf("최근 분위기", "내 직관 기록", "응원 포인트")
        private const val NEWS_PER_QUERY = 3
        private const val MAX_NEWS_REFERENCES = 5
        private val TEAM_KEYWORDS = mapOf(
            "samsung-lions" to listOf("삼성", "삼성 라이온즈"),
            "kia-tigers" to listOf("KIA", "기아", "KIA 타이거즈", "기아 타이거즈"),
            "hanwha-eagles" to listOf("한화", "한화 이글스"),
            "lg-twins" to listOf("LG", "엘지", "LG 트윈스", "엘지 트윈스"),
            "doosan-bears" to listOf("두산", "두산 베어스"),
            "lotte-giants" to listOf("롯데", "롯데 자이언츠"),
            "ssg-landers" to listOf("SSG", "SSG 랜더스"),
            "kt-wiz" to listOf("KT", "케이티", "KT 위즈", "케이티 위즈"),
            "nc-dinos" to listOf("NC", "엔씨", "NC 다이노스", "엔씨 다이노스"),
            "kiwoom-heroes" to listOf("키움", "키움 히어로즈"),
        )
        private val UNRELATED_PLAYER_KEYWORDS = listOf("최형우")
        private val BANNED_PATTERNS = listOf(
            Regex("베팅"),
            Regex("도박"),
            Regex("배당"),
            Regex("적중률"),
            Regex("확률"),
            Regex("승리\\s*확률"),
            Regex("예측\\s*확률"),
            Regex("odds", RegexOption.IGNORE_CASE),
            Regex("spread", RegexOption.IGNORE_CASE),
            Regex("moneyline", RegexOption.IGNORE_CASE),
        )
    }
}
