package ru.aigram.server

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import ru.aigram.exception.impl.CrossOriginRejectedException
import ru.aigram.exception.impl.ForbiddenHostException
import ru.aigram.exception.impl.SessionRequiredException

private const val LOOPBACK_ADDRESS = "127.0.0.1"
private const val SESSION_COOKIE = "tiho"

class LocalSession(private val token: String) {
    fun url(port: Int): String = "http://$LOOPBACK_ADDRESS:$port/?key=$token"

    fun authorize(request: HttpServletRequest, response: HttpServletResponse) {
        val host = "$LOOPBACK_ADDRESS:${request.localPort}"
        if (request.getHeader("Host") != host) {
            throw ForbiddenHostException()
        }

        if (!request.servletPath.startsWith("/api/")) {
            if (request.queryString == "key=$token") {
                response.addHeader("Set-Cookie", "$SESSION_COOKIE=$token; Path=/; HttpOnly; SameSite=Strict")
            }

            return
        }

        val cookieMatches = request.getHeader("Cookie").orEmpty().split(';').stream()
            .anyMatch { it.trim() == "$SESSION_COOKIE=$token" }
        if (!cookieMatches) {
            throw SessionRequiredException()
        }

        val origin = request.getHeader("Origin")
        if (origin != null && origin != "http://$host") {
            throw CrossOriginRejectedException()
        }
    }
}
