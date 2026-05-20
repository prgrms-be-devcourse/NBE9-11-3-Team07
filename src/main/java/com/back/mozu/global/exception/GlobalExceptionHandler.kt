package com.back.mozu.global.exception

import com.back.mozu.global.response.RsData
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.method.annotation.HandlerMethodValidationException

@ControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(NoSuchElementException::class)
    @ResponseBody
    fun handleException(): RsData<Void> {
        return RsData(
            "존재하지 않는 데이터입니다.",
            "404-1"
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseBody
    fun handleException(e: MethodArgumentNotValidException): RsData<Void> {
        val message = e.bindingResult
            .allErrors
            .filterIsInstance<FieldError>()
            .map { error ->
                "${error.field}-${error.code}-${error.defaultMessage}"
            }
            .sorted()
            .joinToString("\n")

        return RsData(
            message,
            "400-1"
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseBody
    fun handleException(e: HttpMessageNotReadableException): RsData<Void> {
        return RsData(
            "잘못된 형식의 요청 데이터입니다.",
            "400-2"
        )
    }

    @ExceptionHandler(ServiceException::class)
    @ResponseBody
    fun handleException(e: ServiceException): RsData<Void> {
        return e.getRsData()
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    @ResponseBody
    fun handleException(e: HandlerMethodValidationException): RsData<Void> {
        return RsData(
            "잘못된 파라미터 요청입니다.",
            "400-4"
        )
    }
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseBody
    fun handleException(e: IllegalArgumentException): RsData<Void> {
        return RsData(
            e.message ?: "잘못된 요청입니다.",
            "409-1"
        )
    }
}
