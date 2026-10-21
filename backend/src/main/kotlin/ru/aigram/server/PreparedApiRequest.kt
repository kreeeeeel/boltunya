package ru.aigram.server

import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Collections
import java.util.Enumeration

internal class PreparedApiRequest(request: HttpServletRequest, private val bytes: ByteArray) :
    HttpServletRequestWrapper(request) {
    private val type = request.contentType ?: "application/json"

    override fun getInputStream(): ServletInputStream = ByteArrayServletInputStream(bytes)

    override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

    override fun getContentType(): String = type

    override fun getContentLength(): Int = bytes.size

    override fun getContentLengthLong(): Long = bytes.size.toLong()

    override fun getHeader(name: String): String? = when {
        name.equals("Content-Type", ignoreCase = true) -> type
        name.equals("Content-Length", ignoreCase = true) -> bytes.size.toString()
        else -> super.getHeader(name)
    }

    override fun getHeaders(name: String): Enumeration<String> {
        if (name.equals("Content-Type", ignoreCase = true) || name.equals("Content-Length", ignoreCase = true)) {
            return Collections.enumeration(listOfNotNull(getHeader(name)))
        }

        return super.getHeaders(name)
    }
}
