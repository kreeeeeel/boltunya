package ru.aigram.server

import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.stereotype.Component
import ru.aigram.exception.impl.RequestFailedException
import java.util.concurrent.atomic.AtomicBoolean

@Component
class LocalApiServer(private val context: WebServerApplicationContext, private val session: LocalSession) :
    AutoCloseable {
    private val closed = AtomicBoolean()

    fun start(): String {
        val server = context.webServer
        if (server == null) {
            throw RequestFailedException()
        }

        return session.url(server.port)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        context.webServer?.stop()
    }
}
