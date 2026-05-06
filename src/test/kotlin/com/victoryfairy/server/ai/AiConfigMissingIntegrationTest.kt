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
        "spring.datasource.url=jdbc:h2:mem:ai-config-missing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "victory-fairy.ai.diary-enabled=true",
        "victory-fairy.ai.groq-api-key=",
    ],
)
class AiConfigMissingIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `AI enabled without key returns AI_CONFIG_MISSING`() {
        mockMvc.post("/api/v1/ai/diary-draft") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "gameDate":"2026-04-16",
                  "favoriteTeamName":"한화 이글스",
                  "opponentTeamName":"삼성 라이온즈",
                  "stadiumName":"대전 한화생명 볼파크",
                  "result":"loss",
                  "scoreText":"1:6 패"
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(false) } }
            .andExpect { jsonPath("$.error.code") { value("AI_CONFIG_MISSING") } }
    }
}
