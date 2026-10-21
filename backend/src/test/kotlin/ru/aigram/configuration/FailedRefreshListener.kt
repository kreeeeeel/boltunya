package ru.aigram.configuration

import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent
import org.springframework.context.ApplicationListener

class FailedRefreshListener(private val onReady: (ServletWebServerApplicationContext) -> Unit) :
    ApplicationListener<ServletWebServerInitializedEvent> {
    override fun onApplicationEvent(event: ServletWebServerInitializedEvent) {
        onReady(event.applicationContext)
    }
}
