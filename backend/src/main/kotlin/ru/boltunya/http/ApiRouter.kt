package ru.boltunya.http

import com.sun.net.httpserver.HttpExchange
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import ru.boltunya.exception.impl.MethodNotAllowedException
import ru.boltunya.exception.impl.RouteNotFoundException
import ru.boltunya.http.endpoint.ActivityEndpoint
import ru.boltunya.http.endpoint.AuthenticationEndpoint
import ru.boltunya.http.endpoint.ChatEndpoint
import ru.boltunya.http.endpoint.DiagnosticsEndpoint
import ru.boltunya.http.endpoint.EngineEndpoint
import ru.boltunya.http.endpoint.FlowEndpoint
import ru.boltunya.http.endpoint.MediaEndpoint
import ru.boltunya.http.endpoint.MemoryEndpoint
import ru.boltunya.http.endpoint.PreviewEndpoint
import ru.boltunya.http.endpoint.SearchEndpoint
import ru.boltunya.http.endpoint.SettingsEndpoint
import ru.boltunya.http.endpoint.StateEndpoint
import ru.boltunya.monitoring.MonitoringService
import ru.boltunya.telegram.TelegramCacheStorage

private const val RESOURCE_SEGMENT = 2
private const val ID_SEGMENT = 3
private const val ACTION_SEGMENT = 4
private const val ITEM_SEGMENTS = 4
private const val ACTION_SEGMENTS = 5
private const val MEDIA_PREFIX = "/api/media/"

class ApiRouter @Inject constructor(
    private val json: Json,
    private val input: RequestReader,
    private val response: ResponseWriter,
    private val state: StateEndpoint,
    private val authentication: AuthenticationEndpoint,
    private val settings: SettingsEndpoint,
    private val chats: ChatEndpoint,
    private val preview: PreviewEndpoint,
    private val flows: FlowEndpoint,
    private val engine: EngineEndpoint,
    private val media: MediaEndpoint,
    private val monitor: MonitoringService,
    private val memory: MemoryEndpoint,
    private val exit: ExitAction,
    private val validation: InputValidation,
    private val cache: TelegramCacheStorage,
    private val search: SearchEndpoint,
    private val activity: ActivityEndpoint,
    private val diagnostics: DiagnosticsEndpoint,
) : AutoCloseable {
    fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val method =
            HttpMethod.entries.firstOrNull { it.name == exchange.requestMethod } ?: throw MethodNotAllowedException()
        if (path.startsWith(MEDIA_PREFIX) &&
            method == HttpMethod.GET
        ) {
            media.get(exchange, path.removePrefix(MEDIA_PREFIX))
            return
        }
        val body = if (method == HttpMethod.GET) JsonObject(emptyMap()) else input.read(exchange)
        val route = ApiRoute.entries.firstOrNull { it.path == path && it.method == method }
        val result = when (route) {
            ApiRoute.STATE -> json.encodeToJsonElement(state.get())
            ApiRoute.ACTIVITY_CLEAR -> {
                activity.clear()
                ok()
            }
            ApiRoute.ACTIVITY_QUEUE -> {
                activity.pause(body)
                ok()
            }
            ApiRoute.SEARCH_CONNECTION -> json.encodeToJsonElement(search.connection())
            ApiRoute.SEARCH_KEY_SAVE -> json.encodeToJsonElement(search.save(body))
            ApiRoute.SEARCH_KEY_DELETE -> json.encodeToJsonElement(search.delete())
            ApiRoute.SEARCH_CHECK -> json.encodeToJsonElement(search.check())
            ApiRoute.METRICS -> json.encodeToJsonElement(monitor.history())
            ApiRoute.CACHE -> json.encodeToJsonElement(cache.statistics())
            ApiRoute.CACHE_CLEAR -> json.encodeToJsonElement(cache.clear())
            ApiRoute.DIAGNOSTIC_ERRORS -> json.encodeToJsonElement(diagnostics.errors())
            ApiRoute.DIAGNOSTIC_ERRORS_CLEAR -> {
                diagnostics.clear()
                ok()
            }
            ApiRoute.STORAGE_LOCATIONS -> json.encodeToJsonElement(diagnostics.locations())
            ApiRoute.MEMORY -> json.encodeToJsonElement(memory.get(""))
            ApiRoute.MEMORY_EDIT -> json.encodeToJsonElement(memory.edit("", body))
            ApiRoute.MEMORY_CANCEL -> json.encodeToJsonElement(memory.cancel(""))
            ApiRoute.MEMORY_REFRESH -> {
                memory.refreshChanged(body)
                ok()
            }
            ApiRoute.SETTINGS -> json.encodeToJsonElement(settings.update(body))
            ApiRoute.CONNECT -> {
                authentication.connect(body)
                ok()
            }
            ApiRoute.SUBMIT -> {
                authentication.submit(body)
                ok()
            }
            ApiRoute.RESTORE -> {
                authentication.restore()
                ok()
            }
            ApiRoute.LOGOUT -> {
                authentication.logout()
                ok()
            }
            ApiRoute.PREVIEW -> json.encodeToJsonElement(preview.reply(body))
            ApiRoute.FLOWS -> json.encodeToJsonElement(flows.list())
            ApiRoute.ENGINE_INSTALL, ApiRoute.ENGINE_START, ApiRoute.MODEL_PULL, ApiRoute.MODEL_CANCEL -> {
                engine.act(route, body)
                ok()
            }
            ApiRoute.QUIT -> {
                exit.request()
                ok()
            }
            null -> dynamic(exchange, method, body)
        }
        response.json(exchange, result)
    }

    private fun dynamic(exchange: HttpExchange, method: HttpMethod, body: JsonObject): JsonElement {
        val parts = exchange.requestURI.path.split('/')
        if (parts.size >= ITEM_SEGMENTS && parts[RESOURCE_SEGMENT] == "activity") {
            val id = parts[ID_SEGMENT]
            return when {
                parts.size == ITEM_SEGMENTS && method == HttpMethod.GET -> json.encodeToJsonElement(
                    activity.details(id),
                )
                parts.size == ACTION_SEGMENTS && method == HttpMethod.POST && parts[ACTION_SEGMENT] == "cancel" -> {
                    activity.cancel(id)
                    ok()
                }
                parts.size == ACTION_SEGMENTS && method == HttpMethod.POST && parts[ACTION_SEGMENT] == "priority" -> {
                    activity.prioritize(id)
                    ok()
                }
                else -> throw RouteNotFoundException()
            }
        }
        if (parts.size == ACTION_SEGMENTS && parts[RESOURCE_SEGMENT] == "media" &&
            parts[ACTION_SEGMENT] == "download" && method == HttpMethod.POST
        ) {
            media.download(parts[ID_SEGMENT])
            return ok()
        }
        if (parts.size == ACTION_SEGMENTS && parts[RESOURCE_SEGMENT] == "memory" && parts[ACTION_SEGMENT] == "cancel") {
            if (method != HttpMethod.POST) throw MethodNotAllowedException()
            return json.encodeToJsonElement(memory.cancel(parts[ID_SEGMENT]))
        }
        if (parts.size == ITEM_SEGMENTS && parts[RESOURCE_SEGMENT] == "memory") {
            val id = parts[ID_SEGMENT]
            return when (method) {
                HttpMethod.GET -> json.encodeToJsonElement(memory.get(id))
                HttpMethod.PATCH -> json.encodeToJsonElement(memory.edit(id, body))
                HttpMethod.POST -> json.encodeToJsonElement(memory.refresh(id, body))
                else -> throw MethodNotAllowedException()
            }
        }
        if (parts.size == ACTION_SEGMENTS && parts[RESOURCE_SEGMENT] == "chats") {
            val id = parts[ID_SEGMENT]
            return when {
                parts[ACTION_SEGMENT] == "deleted" && method == HttpMethod.GET -> json.encodeToJsonElement(
                    chats.deleted(
                        id,
                        exchange.requestURI.query?.split('&')?.firstOrNull {
                            it.startsWith("before=")
                        }?.substringAfter('=')?.toLongOrNull()
                            ?: 0,
                    ),
                )
                parts[ACTION_SEGMENT] == "messages" && method == HttpMethod.GET -> json.encodeToJsonElement(
                    chats.history(
                        id,
                        exchange.requestURI.query?.split('&')?.firstOrNull {
                            it.startsWith("before=")
                        }?.substringAfter('=')?.toLongOrNull()
                            ?: 0,
                    ),
                )
                parts[ACTION_SEGMENT] == "messages" && method == HttpMethod.POST -> {
                    chats.send(id, body)
                    ok()
                }
                parts[ACTION_SEGMENT] == "rule" && method == HttpMethod.PUT -> {
                    chats.saveRule(id, body)
                    ok()
                }
                else -> throw RouteNotFoundException()
            }
        }
        if (parts.size >= ITEM_SEGMENTS && parts[RESOURCE_SEGMENT] == "flows") {
            val id = parts[ID_SEGMENT]
            return when {
                parts.size == ITEM_SEGMENTS && method == HttpMethod.PUT -> {
                    flows.save(id, body)
                    ok()
                }
                parts.size == ITEM_SEGMENTS && method == HttpMethod.DELETE -> {
                    flows.delete(id)
                    ok()
                }
                parts.size == ACTION_SEGMENTS && parts[ACTION_SEGMENT] == "test" &&
                    method == HttpMethod.POST -> json.encodeToJsonElement(
                    flows.preview(id, body),
                )
                else -> throw RouteNotFoundException()
            }
        }
        throw RouteNotFoundException()
    }

    private fun ok(): JsonObject = buildJsonObject { put("ok", true) }
    override fun close() {
        validation.close()
    }
}
