package ru.aigram.server

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import ru.aigram.contract.dto.Error
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.domain.events.diagnostics.DiagnosticSource
import ru.aigram.exception.AppException
import ru.aigram.exception.impl.RequestFailedException

@Component
class ApiErrors(private val diagnostics: DiagnosticLog) {
    fun response(exception: Exception): ResponseEntity<Error> {
        val failure = if (exception is AppException) {
            exception
        } else {
            RequestFailedException()
        }

        if (failure.status >= 500) {
            diagnostics.record(DiagnosticSource.HTTP, exception)
        }

        return ResponseEntity.status(failure.status).body(Error(failure.message, failure.code.name))
    }
}
