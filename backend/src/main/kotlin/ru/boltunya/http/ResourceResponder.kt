package ru.boltunya.http

import com.sun.net.httpserver.HttpExchange
import jakarta.inject.Inject
import ru.boltunya.exception.impl.MethodNotAllowedException
import ru.boltunya.exception.impl.RouteNotFoundException

private const val HTTP_OK = 200
private val CONTENT_TYPES = mapOf(
    "html" to "text/html; charset=utf-8", "js" to "text/javascript; charset=utf-8", "css" to "text/css; charset=utf-8",
    "json" to "application/json", "svg" to "image/svg+xml", "png" to "image/png", "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg", "webp" to "image/webp",
    "woff2" to "font/woff2", "woff" to "font/woff", "ico" to "image/x-icon",
)

class ResourceResponder @Inject constructor(private val response: ResponseWriter) {
    fun respond(exchange: HttpExchange) {
        if (exchange.requestMethod != HttpMethod.GET.name) throw MethodNotAllowedException()
        val path = exchange.requestURI.path
        if (path.contains("..") || path.contains('\\')) throw RouteNotFoundException()
        val resource = if (path.endsWith('/')) "$path/index.html".replace("//", "/") else path
        val input = javaClass.getResourceAsStream("/web$resource") ?: throw RouteNotFoundException()
        exchange.responseHeaders.set(
            "Content-Type",
            CONTENT_TYPES[resource.substringAfterLast('.')] ?: "application/octet-stream",
        )
        input.use { response.bytes(exchange, HTTP_OK, it.readAllBytes()) }
    }
}
