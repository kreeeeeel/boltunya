package ru.aigram.http

import com.sun.net.httpserver.HttpExchange
import ru.aigram.exception.impl.CrossOriginRejectedException
import ru.aigram.exception.impl.ForbiddenHostException
import ru.aigram.exception.impl.SessionRequiredException

private const val LOOPBACK_ADDRESS = "127.0.0.1"
private const val SESSION_COOKIE = "tiho"

class LocalSession(private val token: String) {
    fun url(port: Int): String = "http://$LOOPBACK_ADDRESS:$port/?key=$token"

    fun authorize(exchange: HttpExchange, port: Int) {
        val host = "$LOOPBACK_ADDRESS:$port"
        if (exchange.requestHeaders.getFirst("Host") != host) throw ForbiddenHostException()
        if (!exchange.requestURI.path.startsWith("/api/")) {
            if (exchange.requestURI.rawQuery ==
                "key=$token"
            ) {
                exchange.responseHeaders.add(
                    "Set-Cookie",
                    "$SESSION_COOKIE=$token; Path=/; HttpOnly; SameSite=Strict",
                )
            }
            return
        }
        if (!exchange.requestHeaders.getFirst("Cookie").orEmpty().split(';').any {
                it.trim() == "$SESSION_COOKIE=$token"
            }
        ) {
            throw SessionRequiredException()
        }
        val origin = exchange.requestHeaders.getFirst("Origin")
        if (origin != null && origin != "http://$host") throw CrossOriginRejectedException()
    }
}
