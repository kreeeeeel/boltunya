package ru.aigram.ai.research

import ru.aigram.resources.ResourceCatalog
import java.net.URI
import java.util.Locale

private const val MAX_URL_CHARACTERS = 2048

class ResearchUrl {
    fun canonical(value: String): String {
        if (value.length > MAX_URL_CHARACTERS) return ""
        val uri = try {
            URI.create(value).normalize()
        } catch (_: IllegalArgumentException) {
            return ""
        }
        if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) return ""
        val query = uri.rawQuery.orEmpty().split('&')
            .filter { parameter ->
                val name = parameter.substringBefore('=').lowercase(Locale.ROOT)
                parameter.isNotBlank() && !name.startsWith("utm_") && name !in setOf("gclid", "fbclid", "yclid")
            }
            .joinToString("&")
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        return uri.scheme.lowercase(Locale.ROOT) + "://" + uri.rawAuthority.lowercase(Locale.ROOT) + path +
            if (query.isEmpty()) "" else "?$query"
    }

    fun host(value: String): String = URI.create(value).host.orEmpty().lowercase(Locale.ROOT).removePrefix("www.")
}
