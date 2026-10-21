package ru.aigram.controller

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import ru.aigram.exception.impl.RouteNotFoundException

private val CONTENT_TYPES = mapOf(
    "html" to "text/html; charset=utf-8", "js" to "text/javascript; charset=utf-8", "css" to "text/css; charset=utf-8",
    "json" to "application/json", "svg" to "image/svg+xml", "png" to "image/png", "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg", "webp" to "image/webp", "woff2" to "font/woff2", "woff" to "font/woff",
    "ico" to "image/x-icon",
)

@RestController
class StaticResourceController {
    @GetMapping("/{*path}")
    fun resource(request: HttpServletRequest): ResponseEntity<Resource> {
        val path = request.servletPath
        if (path.startsWith("/api/") || path.contains("..") || path.contains('\\')) {
            throw RouteNotFoundException()
        }

        val relative = if (path.endsWith('/')) {
            "${path}index.html"
        } else {
            path
        }
        val resource = ClassPathResource("web$relative")
        if (!resource.exists() || !resource.isReadable) {
            throw RouteNotFoundException()
        }

        val type = CONTENT_TYPES[relative.substringAfterLast('.')] ?: "application/octet-stream"
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).body(resource)
    }
}
