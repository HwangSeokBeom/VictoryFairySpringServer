package com.victoryfairy.server.kbo

import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.config.AppProperties
import com.victoryfairy.server.kbo.collector.KboDevEndpointGuard
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.springframework.mock.env.MockEnvironment

class KboDevEndpointGuardTest {
    @Test
    fun `guard blocks production profile`() {
        val environment = MockEnvironment().withProperty("spring.profiles.active", "production")
        environment.setActiveProfiles("production")
        val guard = KboDevEndpointGuard(AppProperties(), environment)

        val error = assertFailsWith<ApiException> { guard.assertAllowed(null) }

        kotlin.test.assertEquals("KBO_SCRAPED_DEV_FORBIDDEN", error.code)
    }

    @Test
    fun `guard requires admin token when configured`() {
        val properties = AppProperties(
            kbo = AppProperties.KboProperties(
                scrapedDev = AppProperties.ScrapedDevProperties(adminImportToken = "secret")
            )
        )
        val environment = MockEnvironment()
        environment.setActiveProfiles("test")
        val guard = KboDevEndpointGuard(properties, environment)

        assertFailsWith<ApiException> { guard.assertAllowed("wrong") }
        guard.assertAllowed("secret")
    }
}
