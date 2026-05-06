package com.victoryfairy.server.ticket

import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:ticket-ocr;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class TicketOcrIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `ticket OCR parses date team stadium and seat`() {
        mockMvc.post("/api/v1/ticket/parse-ocr-text") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "ocrText": "2026.04.16\n한화 이글스 vs 삼성 라이온즈\n대전 한화생명 볼파크\n1루 204블록 12열 8번",
                  "locale": "ko-KR"
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data.candidates[0].date") { value("2026-04-16") } }
            .andExpect { jsonPath("$.data.candidates[0].homeTeamID") { value("hanwha-eagles") } }
            .andExpect { jsonPath("$.data.candidates[0].awayTeamID") { value("samsung-lions") } }
            .andExpect { jsonPath("$.data.candidates[0].stadiumName") { value("대전 한화생명 볼파크") } }
            .andExpect { jsonPath("$.data.candidates[0].seatText") { value("1루 204블록 12열 8번") } }
    }

    @Test
    fun `ticket OCR handles ambiguous team order`() {
        mockMvc.post("/api/v1/ticket/parse-ocr-text") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "ocrText": "4월 16일\n삼성 라이온즈 / 한화 이글스\n대전\n응원석 12열 8번",
                  "locale": "ko-KR"
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.candidates[0].homeTeamID") { value("samsung-lions") } }
            .andExpect { jsonPath("$.data.candidates[0].awayTeamID") { value("hanwha-eagles") } }
            .andExpect { jsonPath("$.data.candidates[0].warnings", hasItem("TEAM_ORDER_UNCERTAIN")) }
    }
}
