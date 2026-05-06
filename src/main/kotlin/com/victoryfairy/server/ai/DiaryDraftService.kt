package com.victoryfairy.server.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.config.AppProperties
import java.text.Normalizer
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

private const val DIARY_SAFETY_NOTICE = "AI 초안은 저장 전 사용자가 직접 확인해 주세요."

@Service
class DiaryDraftService(
    private val properties: AppProperties,
    private val groqClient: GroqClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val aiUsage = ConcurrentHashMap<String, UsageCounter>()

    fun createAiDraft(request: DiaryDraftRequest, rateLimitKey: String): DiaryDraftData {
        if (!properties.ai.diaryEnabled) {
            throw ApiException("AI_FEATURE_DISABLED", "AI 후기 초안 기능은 비활성화되어 있습니다.", 200)
        }
        if (properties.ai.groqApiKey.isBlank()) {
            throw ApiException("AI_CONFIG_MISSING", "AI 설정이 완료되지 않았습니다.", 200)
        }
        val normalized = normalizeAndValidate(request)
        checkRateLimit(rateLimitKey)

        val model = properties.ai.groqModel.ifBlank { "llama-3.1-8b-instant" }
        val requestID = java.util.UUID.randomUUID().toString()
        val started = System.nanoTime()
        try {
            val result = groqClient.requestDraft(
                apiKey = properties.ai.groqApiKey,
                model = model,
                prompt = buildPrompt(normalized),
                timeout = Duration.ofSeconds(properties.ai.timeoutSeconds.coerceIn(3, 15)),
            )
            val parsed = parseAndValidate(result.content, model, "groq")
                ?: groqClient.requestRepair(
                    apiKey = properties.ai.groqApiKey,
                    model = model,
                    invalidContent = result.content,
                    timeout = Duration.ofSeconds(properties.ai.timeoutSeconds.coerceIn(3, 15)),
                ).let { parseAndValidate(it.content, model, "groq") }
                ?: throw ApiException("AI_DRAFT_INVALID_RESPONSE", "AI 응답 형식이 올바르지 않습니다.", 200)

            logMetadata(requestID, model, "success", started, result)
            return parsed
        } catch (error: ApiException) {
            logMetadata(requestID, model, error.code, started, null)
            throw error
        } catch (error: Exception) {
            logMetadata(requestID, model, "AI_DRAFT_FAILED", started, null)
            throw ApiException(
                "AI_DRAFT_FAILED",
                "AI 초안 생성에 실패했습니다. 템플릿 초안을 사용할 수 있습니다.",
                200,
                mapOf("fallbackAvailable" to true),
            )
        }
    }

    fun createTemplateDraft(request: DiaryDraftRequest): DiaryDraftData {
        val normalized = normalizeAndValidate(request, allowExtraNoteAlias = true)
        val resultWord = when (normalized.result) {
            "win" -> "승리"
            "loss" -> "패배"
            "draw" -> "무승부"
            "canceled" -> "취소"
            else -> normalized.result
        }
        val tonePrefix = when (normalized.tone) {
            "cheerful" -> "신나게"
            "calm" -> "차분하게"
            "warm" -> "따뜻하게"
            else -> "담백하게"
        }
        val tagsText = (normalized.moodTags + normalized.highlightTags).distinct().take(4).joinToString(", ")
        val extra = normalized.extraNoteSanitized?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        val tagSentence = if (tagsText.isBlank()) "" else " 오늘의 키워드는 ${tagsText}였다."
        val draft = "$tonePrefix ${normalized.stadiumName}에서 ${normalized.favoriteTeamName}와 ${normalized.opponentTeamName}의 경기를 직관했다. 결과는 ${normalized.scoreText}로 기록했다.$tagSentence$extra 저장 전 내 기억에 맞게 다시 확인해야겠다."
        val summary = "${normalized.favoriteTeamName} ${normalized.scoreText} 직관"
        val share = "${normalized.gameDate} ${normalized.stadiumName} 직관 기록"
        return DiaryDraftData(
            draftText = draft.take(1200),
            summaryText = summary.take(80),
            shareText = share.take(120),
            hashtags = buildHashtags(normalized),
            source = "template",
            safetyNotice = DIARY_SAFETY_NOTICE,
        )
    }

    fun rejectUnsafeJson(body: JsonNode) {
        val unsafeKeys = setOf("photo", "image", "imageBase64", "base64", "ticketImage", "photoBinary")
        val found = collectFieldNames(body).filter { it in unsafeKeys }
        if (found.isNotEmpty()) throw ApiException("VALIDATION_ERROR", "사진이나 base64 데이터는 이 엔드포인트로 보낼 수 없습니다.")
    }

    private fun collectFieldNames(node: JsonNode): List<String> {
        val names = mutableListOf<String>()
        if (node.isObject) {
            node.fieldNames().forEachRemaining { name ->
                names += name
                names += collectFieldNames(node.get(name))
            }
        } else if (node.isArray) {
            node.forEach { names += collectFieldNames(it) }
        }
        return names
    }

    private fun checkRateLimit(rateLimitKey: String) {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val counter = aiUsage.compute(rateLimitKey) { _, existing ->
            if (existing == null || existing.date != today) UsageCounter(today, 1) else existing.copy(count = existing.count + 1)
        } ?: UsageCounter(today, 1)
        if (counter.count > properties.ai.dailyLimit.coerceAtLeast(1)) {
            throw ApiException("AI_DAILY_LIMIT_EXCEEDED", "오늘 사용할 수 있는 AI 초안 생성 횟수를 초과했습니다.", 200)
        }
    }

    private fun normalizeAndValidate(request: DiaryDraftRequest, allowExtraNoteAlias: Boolean = false): DiaryDraftRequest {
        fun required(value: String?, name: String): String =
            sanitizeText(value).takeIf { it.isNotBlank() } ?: throw ApiException("VALIDATION_ERROR", "$name 값이 필요합니다.")

        val date = required(request.gameDate, "gameDate")
        runCatching { LocalDate.parse(date) }.getOrElse { throw ApiException("VALIDATION_ERROR", "gameDate 형식은 YYYY-MM-DD여야 합니다.") }
        val extra = sanitizeText(request.extraNoteSanitized ?: if (allowExtraNoteAlias) request.extraNote else null)
        if (extra.length > 300) throw ApiException("VALIDATION_ERROR", "extraNoteSanitized는 300자 이하여야 합니다.")
        if (request.moodTags.size > 5 || request.highlightTags.size > 5) {
            throw ApiException("VALIDATION_ERROR", "moodTags와 highlightTags는 각각 최대 5개까지 가능합니다.")
        }
        val companion = request.companionType?.trim()?.lowercase()?.takeIf {
            it in setOf("alone", "friends", "family", "partner", "coworkers", "unknown")
        }?.takeUnless { it == "unknown" }

        val result = required(request.result, "result").lowercase()
        if (result !in setOf("win", "loss", "draw", "canceled")) {
            throw ApiException("VALIDATION_ERROR", "result 값이 올바르지 않습니다.")
        }

        return request.copy(
            gameDate = date,
            favoriteTeamName = required(request.favoriteTeamName, "favoriteTeamName"),
            opponentTeamName = required(request.opponentTeamName, "opponentTeamName"),
            stadiumName = required(request.stadiumName, "stadiumName"),
            result = result,
            scoreText = required(request.scoreText, "scoreText"),
            moodTags = request.moodTags.map(::sanitizeText).filter { it.isNotBlank() }.take(5),
            highlightTags = request.highlightTags.map(::sanitizeText).filter { it.isNotBlank() }.take(5),
            companionType = companion,
            tone = request.tone?.trim()?.lowercase()?.takeIf { it in setOf("warm", "calm", "cheerful", "short") } ?: "warm",
            extraNoteSanitized = extra,
            locale = request.locale?.takeIf { it.isNotBlank() } ?: "ko-KR",
        )
    }

    private fun sanitizeText(value: String?): String {
        if (value == null) return ""
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val injectionHints = listOf("ignore previous", "system prompt", "developer message", "api key", "비밀번호", "프롬프트를 무시")
        return injectionHints.fold(normalized) { current, hint ->
            current.replace(Regex(Regex.escape(hint), RegexOption.IGNORE_CASE), "")
        }.trim()
    }

    private fun buildPrompt(request: DiaryDraftRequest): String =
        """
        VictoryFairy는 KBO 직관 시즌 다이어리 앱입니다. 사용자가 저장 전에 직접 편집할 한국어 초안만 생성하세요.
        반드시 JSON object 하나만 반환하세요. 필드는 draftText, summaryText, shareText, hashtags 입니다.

        정책:
        - 한국어만 사용합니다.
        - 제공된 경기 데이터만 사용합니다.
        - 선수 이름, 공식 기록, 이닝별 사건, 순위, standings, 현장 사건을 새로 만들지 않습니다.
        - "내가 가서 이겼다" 같은 인과 표현을 쓰지 않습니다.
        - 패배 경기는 아쉬움을 존중하되 선수나 팀을 조롱하거나 비난하지 않습니다.
        - 저장 전 확인이 필요한 editable draft로 표현합니다.
        - draftText는 1200자 이하, summaryText는 80자 이하, shareText는 120자 이하입니다.
        - hashtags는 최대 6개이고 각 항목은 #으로 시작합니다.

        입력:
        gameDate=${request.gameDate}
        favoriteTeamName=${request.favoriteTeamName}
        opponentTeamName=${request.opponentTeamName}
        stadiumName=${request.stadiumName}
        result=${request.result}
        scoreText=${request.scoreText}
        moodTags=${request.moodTags}
        highlightTags=${request.highlightTags}
        companionType=${request.companionType ?: ""}
        tone=${request.tone}
        extraNoteSanitized=${request.extraNoteSanitized ?: ""}
        locale=${request.locale ?: "ko-KR"}
        """.trimIndent()

    private fun parseAndValidate(content: String, model: String, source: String): DiaryDraftData? {
        val json = extractJsonObject(content) ?: return null
        val node = runCatching { objectMapper.readTree(json) }.getOrNull() ?: return null
        val draftText = node.path("draftText").asText("").trim()
        val summaryText = node.path("summaryText").asText("").trim()
        val shareText = node.path("shareText").asText("").trim()
        val hashtags = node.path("hashtags").takeIf { it.isArray }?.map { it.asText("").trim() }?.filter { it.startsWith("#") }?.take(6) ?: emptyList()
        if (draftText.isBlank() || draftText.length > 1200 || summaryText.length > 80 || shareText.length > 120) return null
        if (hashtags.any { it.length > 40 }) return null
        return DiaryDraftData(
            draftText = draftText,
            summaryText = summaryText,
            shareText = shareText,
            hashtags = hashtags,
            model = model,
            source = source,
            safetyNotice = DIARY_SAFETY_NOTICE,
        )
    }

    private fun extractJsonObject(content: String): String? {
        val trimmed = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else null
    }

    private fun buildHashtags(request: DiaryDraftRequest): List<String> {
        val stadiumTag = "#" + (request.stadiumName ?: "KBO직관").replace(Regex("[^0-9A-Za-z가-힣]"), "")
        return listOf("#승리요정", "#KBO직관", stadiumTag)
            .filter { it.length > 1 }
            .distinct()
            .take(6)
    }

    private fun logMetadata(requestID: String, model: String, status: String, started: Long, result: GroqDraftResult?) {
        val durationMs = (System.nanoTime() - started) / 1_000_000
        log.info(
            "ai_diary_draft requestID={} model={} status={} durationMs={} promptTokens={} completionTokens={} totalTokens={}",
            requestID,
            model,
            status,
            durationMs,
            result?.promptTokens,
            result?.completionTokens,
            result?.totalTokens,
        )
    }

    private data class UsageCounter(val date: LocalDate, val count: Int)
}
