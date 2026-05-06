package com.victoryfairy.server.kbo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.victoryfairy.server.kbo.importer.KboScraperJsonNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals

class KboScraperJsonNormalizerTest {
    private val normalizer = KboScraperJsonNormalizer(jacksonObjectMapper())

    @Test
    fun `normalizer maps statuses cancellation and stadium aliases`() {
        val json = """
            [
              {"date":"2026-04-01","homeTeam":"한화","awayTeam":"삼성","stadium":"대전","gameStatus":"경기 종료","homeScore":1,"awayScore":6},
              {"date":"2026-04-02","homeTeam":"LG","awayTeam":"두산","stadium":"마산","gameStatus":"경기전"},
              {"date":"2026-04-03","homeTeam":"키움","awayTeam":"NC","stadium":"이천(두산)","gameStatus":"경기전","cancellationReason":"우천"}
            ]
        """.trimIndent()

        val result = normalizer.normalize(json, 2026)

        assertEquals(3, result.totalRows)
        assertEquals("final", result.games[0].status)
        assertEquals("scheduled", result.games[1].status)
        assertEquals("canceled", result.games[2].status)
        assertEquals("대전 한화생명 볼파크", result.games[0].stadiumName)
        assertEquals("마산야구장", result.games[1].stadiumName)
        assertEquals("이천베어스파크", result.games[2].stadiumName)
    }
}
