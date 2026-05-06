package com.victoryfairy.server.kbo

import com.victoryfairy.server.kbo.collector.KboScheduleParser
import com.victoryfairy.server.kbo.collector.KboSeriesType
import kotlin.test.Test
import kotlin.test.assertEquals

class KboScheduleParserTest {
    private val parser = KboScheduleParser()

    @Test
    fun `parser maps teams stadium statuses and home away scores`() {
        val html = """
            <table id="tblScheduleList"><tbody>
              <tr>
                <td class="day">04.16(목)</td>
                <td class="time"><b>18:30</b></td>
                <td class="play"><span>삼성</span><em><span class="win">6</span><span>vs</span><span class="lose">1</span></em><span>한화</span></td>
                <td class="relay"><a href="https://www.koreabaseball.com/Schedule/GameCenter/Main.aspx?gameDate=20260416&amp;gameId=20260416SSHH0&amp;section=REVIEW">리뷰</a></td>
                <td></td><td>SPO-T</td><td></td><td>대전</td><td>-</td>
              </tr>
              <tr>
                <td class="day">04.17(금)</td>
                <td class="time"><b>18:30</b></td>
                <td class="play"><span>LG</span><em><span>vs</span></em><span>두산</span></td>
                <td></td><td></td><td></td><td></td><td>잠실</td><td>우천 취소</td>
              </tr>
            </tbody></table>
        """.trimIndent()

        val result = parser.parse(html, 2026, KboSeriesType.REGULAR_SEASON)

        assertEquals(2, result.games.size)
        assertEquals("samsung-lions", result.games[0].awayTeamID)
        assertEquals("hanwha-eagles", result.games[0].homeTeamID)
        assertEquals(6, result.games[0].awayScore)
        assertEquals(1, result.games[0].homeScore)
        assertEquals("final", result.games[0].status)
        assertEquals("대전 한화생명 볼파크", result.games[0].stadiumName)
        assertEquals("canceled", result.games[1].status)
        assertEquals("잠실야구장", result.games[1].stadiumName)
    }

    @Test
    fun `status normalization handles explicit KBO labels`() {
        assertEquals("final", parser.normalizeStatus("경기 종료", null, null))
        assertEquals("scheduled", parser.normalizeStatus("경기전", null, null))
        assertEquals("scheduled", parser.normalizeStatus("예정", null, null))
        assertEquals("canceled", parser.normalizeStatus("경기 취소", null, null))
        assertEquals("postponed", parser.normalizeStatus("순연", null, null))
    }
}
