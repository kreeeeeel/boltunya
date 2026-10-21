package ru.aigram.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import jakarta.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.diagnostics.DiagnosticSource
import ru.aigram.exception.AppException
import ru.aigram.exception.impl.MalformedPayloadException
import ru.aigram.exception.impl.RequestFailedException
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val LOOPBACK_ADDRESS = "127.0.0.1"
private const val LISTEN_BACKLOG = 64

class LocalWebServer @Inject constructor(
    private val session: LocalSession,
    private val router: ApiRouter,
    private val response: ResponseWriter,
    private val resources: ResourceResponder,
    private val diagnostics: DiagnosticLog,
) : AutoCloseable {
    private val closed = AtomicBoolean()
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val server = HttpServer.create(InetSocketAddress(LOOPBACK_ADDRESS, 0), LISTEN_BACKLOG)

    fun start(): String {
        server.executor = executor
        server.createContext("/") { handle(it) }
        server.start()
        return session.url(server.address.port)
    }

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            try {
                exchange.responseHeaders.set("X-Content-Type-Options", "nosniff")
                exchange.responseHeaders.set("Referrer-Policy", "no-referrer")
                exchange.responseHeaders.set("X-Frame-Options", "DENY")
                exchange.responseHeaders.set("Cache-Control", "no-store")
                session.authorize(exchange, server.address.port)
                if (exchange.requestURI.path.startsWith(
                        "/api/",
                    )
                ) {
                    router.handle(exchange)
                } else {
                    resources.respond(exchange)
                }
            } catch (exception: AppException) {
                if (exception.status >= 500) diagnostics.record(DiagnosticSource.HTTP, exception)
                error(exchange, exception)
            } catch (_: SerializationException) {
                error(exchange, MalformedPayloadException())
            } catch (exception: Exception) {
                diagnostics.record(DiagnosticSource.HTTP, exception)
                System.err.println("Local request failed: " + exception.javaClass.simpleName)
                error(exchange, RequestFailedException())
            }
        }
    }

    private fun error(exchange: HttpExchange, exception: AppException) {
        response.json(
            exchange,
            buildJsonObject {
                put("code", exception.code.name)
                put("message", exception.message)
            },
            exception.status,
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        server.stop(0)
        executor.shutdownNow()
        router.close()
    }
}
