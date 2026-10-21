package ru.aigram.http

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.support.GenericApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TestWebServerLifecycleTest {
    @Test
    fun `closes the owned context once without destroying supplied singleton resources`() {
        val application = GenericApplicationContext()
        val ownedCloses = AtomicInteger()
        val suppliedCloses = AtomicInteger()
        application.beanFactory.registerSingleton(
            "suppliedResource",
            AutoCloseable { suppliedCloses.incrementAndGet() },
        )
        application.defaultListableBeanFactory.registerDisposableBean(
            "ownedResource",
            DisposableBean { ownedCloses.incrementAndGet() },
        )
        application.refresh()

        val server = TestWebServer(application)
        server.close()
        server.close()

        assertFalse(application.isActive)
        assertEquals(1, ownedCloses.get())
        assertEquals(0, suppliedCloses.get())
    }
}
