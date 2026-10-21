package ru.aigram.configuration

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.http.ApiRouter
import ru.aigram.http.LocalSession
import ru.aigram.http.LocalWebServer
import ru.aigram.http.ResourceResponder
import ru.aigram.http.ResponseWriter
import ru.aigram.monitoring.MonitoringService
import ru.aigram.monitoring.NvidiaMonitor
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
    fun localWebServer(
        session: LocalSession,
        router: ApiRouter,
        response: ResponseWriter,
        resources: ResourceResponder,
        diagnostics: DiagnosticLog,
    ): LocalWebServer = LocalWebServer(session, router, response, resources, diagnostics)

    @Bean
    fun monitoringService(gpu: NvidiaMonitor): MonitoringService = MonitoringService(gpu)
}
