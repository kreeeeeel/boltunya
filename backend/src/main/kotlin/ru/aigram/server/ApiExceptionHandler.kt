package ru.aigram.server

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import ru.aigram.contract.dto.Error
import ru.aigram.exception.AppException
import ru.aigram.exception.impl.MalformedPayloadException
import ru.aigram.exception.impl.MethodNotAllowedException
import ru.aigram.exception.impl.RouteNotFoundException
import ru.aigram.exception.impl.ValidationFailedException

@RestControllerAdvice
class ApiExceptionHandler(private val errors: ApiErrors) {
    @ExceptionHandler(AppException::class)
    fun application(exception: AppException): ResponseEntity<Error> = errors.response(exception)

    @ExceptionHandler(
        ConstraintViolationException::class,
        MethodArgumentNotValidException::class,
        HandlerMethodValidationException::class,
    )
    fun validation(): ResponseEntity<Error> = errors.response(ValidationFailedException())

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        HttpMediaTypeNotSupportedException::class,
        org.springframework.web.bind.ServletRequestBindingException::class,
    )
    fun malformed(): ResponseEntity<Error> = errors.response(MalformedPayloadException())

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun method(request: HttpServletRequest): ResponseEntity<Error> {
        val failure = if (request.servletPath.startsWith("/api/")) {
            RouteNotFoundException()
        } else {
            MethodNotAllowedException()
        }

        return errors.response(failure)
    }

    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun missing(): ResponseEntity<Error> = errors.response(RouteNotFoundException())

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception): ResponseEntity<Error> = errors.response(exception)
}
