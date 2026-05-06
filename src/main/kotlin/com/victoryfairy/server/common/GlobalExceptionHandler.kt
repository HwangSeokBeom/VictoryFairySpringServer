package com.victoryfairy.server.common

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.core.env.Environment
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

@RestControllerAdvice
class GlobalExceptionHandler(private val environment: Environment) {
    @ExceptionHandler(ApiException::class)
    fun apiException(error: ApiException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(error.httpStatus).body(ApiResponse.fail(error.code, error.message, safeDetails(error.details)))

    @ExceptionHandler(MethodArgumentNotValidException::class, ConstraintViolationException::class, MissingServletRequestParameterException::class)
    fun validation(error: Exception): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse.fail("VALIDATION_ERROR", "입력값을 확인해 주세요.", safeDetails(error.message))
        )

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun maxUploadSize(error: MaxUploadSizeExceededException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            ApiResponse.fail("PROFILE_IMAGE_TOO_LARGE", "프로필 이미지는 2MB 이하로 올려 주세요.", safeDetails(error.message))
        )

    @ExceptionHandler(Exception::class)
    fun unhandled(error: Exception): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResponse.fail("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.", safeDetails(error.message))
        )

    private fun safeDetails(details: Any?): Any? = if (isProductionProfile()) null else details

    private fun isProductionProfile(): Boolean =
        environment.activeProfiles.any { it.equals("prod", ignoreCase = true) || it.equals("production", ignoreCase = true) }
}
