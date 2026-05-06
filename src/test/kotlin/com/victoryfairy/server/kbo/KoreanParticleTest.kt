package com.victoryfairy.server.kbo

import kotlin.test.Test
import kotlin.test.assertEquals

class KoreanParticleTest {
    @Test
    fun `subject particle handles KBO short names`() {
        mapOf(
            "삼성" to "삼성이",
            "한화" to "한화가",
            "키움" to "키움이",
            "두산" to "두산이",
            "롯데" to "롯데가",
            "KIA" to "KIA가",
            "LG" to "LG가",
            "SSG" to "SSG가",
            "KT" to "KT가",
            "NC" to "NC가",
        ).forEach { (input, expected) ->
            assertEquals(expected, KoreanParticle.subject(input))
        }
    }
}
