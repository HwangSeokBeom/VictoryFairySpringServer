package com.victoryfairy.server.community

import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:community-disabled-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "victory-fairy.community.enabled=false",
    ],
)
class CommunityDisabledIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `disabled config returns disabled read state and blocks post`() {
        mockMvc.get("/api/v1/community/posts")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.enabled") { value(false) } }
            .andExpect { jsonPath("$.data.items", hasSize<Any>(0)) }
            .andExpect { jsonPath("$.data.message") { value("응원톡은 준비 중입니다.") } }
            .andExpect { jsonPath("$.data.policyURL") { value("https://hwangseokbeom.github.io/VictoryFairy-legal/community-policy.html") } }

        mockMvc.post("/api/v1/community/posts") {
            header("X-Device-ID", "community-disabled-device")
            contentType = MediaType.APPLICATION_JSON
            content = """{"teamID":"samsung-lions","content":"삼성 화이팅"}"""
        }
            .andExpect { status { isForbidden() } }
            .andExpect { jsonPath("$.success") { value(false) } }
            .andExpect { jsonPath("$.error.code") { value("COMMUNITY_DISABLED") } }
    }
}
