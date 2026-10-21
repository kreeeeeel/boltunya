package ru.aigram.configuration

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.aigram.monitoring.MonitoringService
import ru.aigram.monitoring.NvidiaMonitor
import ru.aigram.server.LocalSession
import java.net.http.HttpClient
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Configuration(proxyBeanMethods = false)
class InfrastructureConfiguration {
    @Bean
    fun applicationDirectory(configuration: ApplicationConfiguration): Path = configuration.directory

    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()

    @Bean
    fun registryHttp(): HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    @Bean
    fun json(): Json = Json { encodeDefaults = true }

    @Bean
    fun localSession(): LocalSession = LocalSession(UUID.randomUUID().toString())

    @Bean
    fun monitoringService(gpu: NvidiaMonitor): MonitoringService = MonitoringService(gpu)
}
