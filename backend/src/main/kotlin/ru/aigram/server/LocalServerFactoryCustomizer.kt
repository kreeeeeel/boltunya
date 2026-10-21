package ru.aigram.server

import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.net.InetAddress

private const val LOOPBACK_ADDRESS = "127.0.0.1"

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class LocalServerFactoryCustomizer : WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {
    override fun customize(factory: ConfigurableServletWebServerFactory) {
        factory.setAddress(InetAddress.getByName(LOOPBACK_ADDRESS))
        factory.setPort(0)
        factory.setContextPath("")
    }
}
