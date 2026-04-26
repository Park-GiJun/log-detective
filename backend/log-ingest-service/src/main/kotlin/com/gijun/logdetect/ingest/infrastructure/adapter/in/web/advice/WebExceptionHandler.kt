package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.advice

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Bean Validation 위반 / 잘못된 enum 값으로 인한 IllegalArgumentException 을
 * 500 이 아닌 400 으로 매핑한다.
 */
@RestControllerAdvice
class WebExceptionHandler {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBodyValidation(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = ex.bindingResult.fieldErrors.map {
            mapOf("field" to it.field, "message" to (it.defaultMessage ?: "invalid"))
        }
        logger.debug("Bean Validation 실패: {}", errors)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "validation_failed", "details" to errors))
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleParamValidation(ex: ConstraintViolationException): ResponseEntity<Map<String, Any>> {
        val errors = ex.constraintViolations.map {
            mapOf("path" to it.propertyPath.toString(), "message" to it.message)
        }
        logger.debug("ConstraintViolation: {}", errors)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "validation_failed", "details" to errors))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, Any>> {
        logger.debug("IllegalArgument: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "bad_request", "message" to (ex.message ?: "invalid request")))
    }
}
