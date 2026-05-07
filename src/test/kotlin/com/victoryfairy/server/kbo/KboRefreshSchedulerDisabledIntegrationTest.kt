package com.victoryfairy.server.kbo

import com.victoryfairy.server.kbo.refresh.KboRefreshScheduler
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:kbo-refresh-scheduler-disabled;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class KboRefreshSchedulerDisabledIntegrationTest {
    @Autowired lateinit var applicationContext: ApplicationContext

    @Test
    fun `scheduler disabled by default`() {
        assertTrue(applicationContext.getBeansOfType(KboRefreshScheduler::class.java).isEmpty())
    }
}
