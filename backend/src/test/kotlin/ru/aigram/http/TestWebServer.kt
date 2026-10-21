package ru.aigram.http

import org.springframework.context.ConfigurableApplicationContext
import java.util.concurrent.atomic.AtomicBoolean

class TestWebServer(private val application: ConfigurableApplicationContext) : AutoCloseable {
    private val closed = AtomicBoolean()

    fun start(): String = application.getBean(LocalWebServer::class.java).start()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            application.close()
        }
    }
}
