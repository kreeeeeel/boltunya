package ru.aigram.server

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import ru.aigram.exception.impl.MediaTooLargeException
import ru.aigram.exception.impl.RequestTooLargeException
import tools.jackson.databind.json.JsonMapper

private const val MAX_JSON_BYTES = 512 * 1024
private const val MAX_UPLOAD_BYTES = 100 * 1024 * 1024
private val BODY_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class LocalApiFilter(private val session: LocalSession, private val errors: ApiErrors, private val json: JsonMapper) :
    OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("Referrer-Policy", "no-referrer")
        response.setHeader("X-Frame-Options", "DENY")
        response.setHeader("Cache-Control", "no-store")

        try {
            session.authorize(request, response)
            chain.doFilter(prepare(request), response)
        } catch (exception: Exception) {
            if (response.isCommitted) {
                throw exception
            }

            val failure = errors.response(exception)
            response.status = failure.statusCode.value()
            response.contentType = "application/json;charset=UTF-8"
            json.writeValue(response.outputStream, failure.body)
        }
    }

    private fun prepare(request: HttpServletRequest): HttpServletRequest {
        if (!request.servletPath.startsWith("/api/") || request.method !in BODY_METHODS) {
            return request
        }

        val upload = request.servletPath == "/api/uploads"
        val maximum = if (upload) {
            MAX_UPLOAD_BYTES
        } else {
            MAX_JSON_BYTES
        }

        if (request.contentLengthLong > maximum) {
            rejectSize(upload)
        }

        val bytes = request.inputStream.readNBytes(maximum + 1)
        if (bytes.size > maximum) {
            rejectSize(upload)
        }

        val body = if (bytes.isEmpty() && !upload) {
            "{}".toByteArray(Charsets.UTF_8)
        } else {
            bytes
        }

        return PreparedApiRequest(request, body)
    }

    private fun rejectSize(upload: Boolean): Nothing {
        if (upload) {
            throw MediaTooLargeException()
        }

        throw RequestTooLargeException()
    }
}
