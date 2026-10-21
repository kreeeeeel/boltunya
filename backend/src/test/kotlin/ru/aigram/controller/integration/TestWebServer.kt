package ru.aigram.controller.integration

import org.springframework.context.ConfigurableApplicationContext
import ru.aigram.server.LocalApiServer
import java.util.concurrent.atomic.AtomicBoolean

class TestWebServer(private val application: ConfigurableApplicationContext) : AutoCloseable {
    private val closed = AtomicBoolean()

    fun start(): String = application.getBean(LocalApiServer::class.java).start()

    fun <T : Any> bean(type: Class<T>): T = application.getBean(type)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            application.close()
        }
    }
}
