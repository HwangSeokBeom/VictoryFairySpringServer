package com.victoryfairy.server.kbo

import com.victoryfairy.server.config.AppProperties
import kotlin.test.Test
import kotlin.test.assertEquals

class KboSourceDisplayPolicyTest {
    @Test
    fun `production mode hides internal scraped dev source marker`() {
        val policy = KboSourceDisplayPolicy(
            AppProperties(
                kbo = AppProperties.KboProperties(sourceLabelMode = "production"),
            ),
        )

        assertEquals("reference", policy.source(SCRAPED_DEV_SOURCE))
        assertEquals("참고용 경기 정보", policy.label(SCRAPED_DEV_SOURCE))
    }

    @Test
    fun `review mode keeps internal source marker for contract tests`() {
        val policy = KboSourceDisplayPolicy(
            AppProperties(
                kbo = AppProperties.KboProperties(sourceLabelMode = "review"),
            ),
        )

        assertEquals(SCRAPED_DEV_SOURCE, policy.source(SCRAPED_DEV_SOURCE))
        assertEquals("참고용 경기 정보", policy.label(SCRAPED_DEV_SOURCE))
    }
}
