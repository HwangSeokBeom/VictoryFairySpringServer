package com.victoryfairy.server.kbo

object KoreanParticle {
    fun subject(value: String): String = value + if (hasFinalConsonant(value)) "이" else "가"

    private fun hasFinalConsonant(value: String): Boolean {
        val last = value.trim().lastOrNull() ?: return false
        if (last !in '\uAC00'..'\uD7A3') return false
        return ((last.code - 0xAC00) % 28) != 0
    }
}
