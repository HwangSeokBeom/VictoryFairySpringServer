package com.victoryfairy.server.common

data class ApiError(
    val code: String,
    val message: String,
    val details: Any? = null,
)

class ApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int = 400,
    val details: Any? = null,
) : RuntimeException(message)
