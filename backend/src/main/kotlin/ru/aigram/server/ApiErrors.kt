package ru.aigram.server

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import ru.aigram.contract.dto.Error
import ru.aigram.exception.AppException
import ru.aigram.exception.impl.RequestFailedException

@Component
class ApiErrors {
    fun response(exception: Exception): ResponseEntity<Error> {
        val failure = if (exception is AppException) {
            exception
        } else {
            RequestFailedException()
        }

        if (failure.status >= 500) {
            System.err.println("API request failed: ${exception.javaClass.simpleName}")
        }

        return ResponseEntity.status(failure.status).body(Error(failure.message, failure.code.name))
    }
}
