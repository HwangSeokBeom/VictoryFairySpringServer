package com.victoryfairy.server.common

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun apiException(error: ApiException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(error.httpStatus).body(ApiResponse.fail(error.code, error.message, error.details))

    @ExceptionHandler(MethodArgumentNotValidException::class, ConstraintViolationException::class, MissingServletRequestParameterException::class)
    fun validation(error: Exception): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse.fail("VALIDATION_ERROR", "입력값을 확인해 주세요.", error.message)
        )

    @ExceptionHandler(Exception::class)
    fun unhandled(error: Exception): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResponse.fail("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.", error.message)
        )
}
