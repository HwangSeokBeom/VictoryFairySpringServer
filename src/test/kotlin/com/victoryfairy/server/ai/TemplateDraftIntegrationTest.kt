package com.victoryfairy.server.ai

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
        "spring.datasource.url=jdbc:h2:mem:template-draft;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class TemplateDraftIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `template draft supports mood highlight tone result score and extra note`() {
        mockMvc.post("/api/v1/diary/template-draft") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "gameDate": "2026-04-16",
                  "favoriteTeamName": "한화 이글스",
                  "opponentTeamName": "삼성 라이온즈",
                  "stadiumName": "대전 한화생명 볼파크",
                  "result": "loss",
                  "scoreText": "1:6 패",
                  "moodTags": ["아쉬움"],
                  "highlightTags": ["응원 분위기"],
                  "tone": "warm",
                  "extraNote": "응원 분위기가 기억에 남았다."
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data.source") { value("template") } }
            .andExpect { jsonPath("$.data.draftText") { value(org.hamcrest.Matchers.containsString("1:6 패")) } }
            .andExpect { jsonPath("$.data.draftText") { value(org.hamcrest.Matchers.containsString("아쉬움, 응원 분위기")) } }
            .andExpect { jsonPath("$.data.draftText") { value(org.hamcrest.Matchers.containsString("응원 분위기가 기억에 남았다.")) } }
    }
}
