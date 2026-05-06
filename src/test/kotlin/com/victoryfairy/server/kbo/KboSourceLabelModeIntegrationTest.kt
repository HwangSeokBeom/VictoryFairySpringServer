package com.victoryfairy.server.kbo

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:kbo-source-review;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "victory-fairy.kbo.source-label-mode=review",
        "victory-fairy.kbo.scraped-dev.enabled=true",
        "victory-fairy.kbo.scraped-dev.admin-import-token=test-admin-token",
    ],
)
class KboSourceLabelModeIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `sourceLabel mode review returns safe scraped-dev wording`() {
        mockMvc.post("/api/v1/dev/kbo/seed-sample-game") {
            header("X-Admin-Token", TEST_ADMIN_TOKEN)
        }
            .andExpect { status { isOk() } }

        mockMvc.get("/api/v1/kbo/games") {
            param("date", "2026-04-16")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.source") { value("scraped-dev") } }
            .andExpect { jsonPath("$.data.sourceLabel") { value("참고용 경기 정보") } }
            .andExpect { jsonPath("$.data.sourceDisclosure") { value("이 정보는 기록 입력을 돕기 위한 참고용 정보이며, 공식 기록은 KBO 공식 사이트에서 확인해 주세요.") } }
            .andExpect { jsonPath("$.data.items[0].sourceLabel") { value("참고용 경기 정보") } }

        mockMvc.get("/api/v1/kbo/standings") {
            param("season", "2026")
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.sourceLabel") { value("참고용 경기 정보") } }
            .andExpect { jsonPath("$.data.sourceDisclosure") { exists() } }
    }

    companion object {
        private const val TEST_ADMIN_TOKEN = "test-admin-token"
    }
}
