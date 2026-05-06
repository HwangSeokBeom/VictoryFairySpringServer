package com.victoryfairy.server.common

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)
        fun fail(code: String, message: String, details: Any? = null): ApiResponse<Nothing> =
            ApiResponse(success = false, error = ApiError(code, message, details))
    }
}
