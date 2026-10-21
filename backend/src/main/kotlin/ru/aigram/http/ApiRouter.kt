package ru.aigram.http

import com.sun.net.httpserver.HttpExchange
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import ru.aigram.ai.profile.ProfileModelFactory
import ru.aigram.exception.impl.MalformedPayloadException
import ru.aigram.exception.impl.MethodNotAllowedException
import ru.aigram.exception.impl.RouteNotFoundException
import ru.aigram.http.endpoint.ActivityEndpoint
import ru.aigram.http.endpoint.AppearanceEndpoint
import ru.aigram.http.endpoint.AttachmentEndpoint
import ru.aigram.http.endpoint.AuthenticationEndpoint
import ru.aigram.http.endpoint.ChatEndpoint
import ru.aigram.http.endpoint.ContextExclusionEndpoint
import ru.aigram.http.endpoint.DiagnosticsEndpoint
import ru.aigram.http.endpoint.DraftEndpoint
import ru.aigram.http.endpoint.EngineEndpoint
import ru.aigram.http.endpoint.MediaEndpoint
import ru.aigram.http.endpoint.MemoryEndpoint
import ru.aigram.http.endpoint.MessageDeletionEndpoint
import ru.aigram.http.endpoint.ModelProfilesEndpoint
import ru.aigram.http.endpoint.PreviewEndpoint
import ru.aigram.http.endpoint.PromptEndpoint
import ru.aigram.http.endpoint.SearchEndpoint
import ru.aigram.http.endpoint.SettingsEndpoint
import ru.aigram.http.endpoint.StateEndpoint
import ru.aigram.http.endpoint.UploadEndpoint
import ru.aigram.monitoring.MonitoringService
import ru.aigram.telegram.TelegramCacheStorage
import java.util.concurrent.atomic.AtomicBoolean

private const val RESOURCE_SEGMENT = 2
private const val ID_SEGMENT = 3
private const val ACTION_SEGMENT = 4
private const val ITEM_SEGMENTS = 4
private const val ACTION_SEGMENTS = 5
private const val MESSAGE_LINK_SEGMENTS = 7
private const val MESSAGE_ITEM_SEGMENTS = 6
private const val MESSAGE_ID_SEGMENT = 5
private const val MESSAGE_ACTION_SEGMENT = 6
private const val MEDIA_PREFIX = "/api/media/"

@Component
class ApiRouter @Inject constructor(
    private val json: Json,
    private val input: RequestReader,
    private val response: ResponseWriter,
    private val state: StateEndpoint,
    private val authentication: AuthenticationEndpoint,
    private val settings: SettingsEndpoint,
    private val chats: ChatEndpoint,
    private val preview: PreviewEndpoint,
    private val drafts: DraftEndpoint,
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
    private val models: ModelProfilesEndpoint,
    private val appearance: AppearanceEndpoint,
    private val modelClients: ProfileModelFactory,
    private val attachments: AttachmentEndpoint,
    private val uploads: UploadEndpoint,
    private val exclusions: ContextExclusionEndpoint,
    private val prompts: PromptEndpoint,
    private val deletions: MessageDeletionEndpoint,
) : AutoCloseable {
    private val closed = AtomicBoolean()
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
        if (path == "/api/uploads" && method == HttpMethod.POST) {
            response.json(exchange, json.encodeToJsonElement(uploads.create(exchange)))
            return
        }
        val body = if (method == HttpMethod.GET) JsonObject(emptyMap()) else input.read(exchange)
        val route = ApiRoute.entries.firstOrNull { it.path == path && it.method == method }
        val result = when (route) {
            ApiRoute.STATE -> json.encodeToJsonElement(state.get())
            ApiRoute.MODEL_PROMPTS -> json.encodeToJsonElement(prompts.list())
            ApiRoute.MODEL_PROMPTS_SAVE -> json.encodeToJsonElement(prompts.save(body))
            ApiRoute.PROMPT_BUDGET -> json.encodeToJsonElement(prompts.budget(body))
            ApiRoute.MODEL_PROFILES -> json.encodeToJsonElement(models.list())
            ApiRoute.MODEL_PROFILES_CREATE -> json.encodeToJsonElement(models.save(null, body))
            ApiRoute.MODEL_ROUTING -> json.encodeToJsonElement(models.routing())
            ApiRoute.MODEL_ROUTING_SAVE -> json.encodeToJsonElement(models.routing(body))
            ApiRoute.APPEARANCE -> json.encodeToJsonElement(appearance.get())
            ApiRoute.APPEARANCE_SAVE -> json.encodeToJsonElement(appearance.update(body))
            ApiRoute.STICKERS -> json.encodeToJsonElement(attachments.catalog())
            ApiRoute.ACTIVITY_HISTORY -> {
                val query = exchange.requestURI.query.orEmpty().split('&').associate {
                    it.substringBefore('=') to it.substringAfter('=', "")
                }
                val limit = query["limit"]?.let { it.toIntOrNull() ?: throw MalformedPayloadException() } ?: 30
                json.encodeToJsonElement(activity.history(query["cursor"], limit))
            }
            ApiRoute.ACTIVITY_HISTORY_SETTINGS -> json.encodeToJsonElement(activity.storage())
            ApiRoute.ACTIVITY_HISTORY_CONFIGURE -> json.encodeToJsonElement(activity.configure(body))
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
            ApiRoute.PREVIEW_JOB -> json.encodeToJsonElement(drafts.enqueue(body))
            ApiRoute.ENGINE_DEVICES -> json.encodeToJsonElement(engine.devices())
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
        if (parts.size == MESSAGE_LINK_SEGMENTS && parts[RESOURCE_SEGMENT] == "chats" &&
            parts[ACTION_SEGMENT] == "messages" && parts[MESSAGE_ACTION_SEGMENT] == "deletion" &&
            method == HttpMethod.GET
        ) {
            return json.encodeToJsonElement(deletions.options(parts[ID_SEGMENT], parts[MESSAGE_ID_SEGMENT]))
        }
        if (parts.size == MESSAGE_ITEM_SEGMENTS && parts[RESOURCE_SEGMENT] == "chats" &&
            parts[ACTION_SEGMENT] == "messages" && method == HttpMethod.DELETE
        ) {
            return json.encodeToJsonElement(deletions.delete(parts[ID_SEGMENT], parts[MESSAGE_ID_SEGMENT], body))
        }
        if (parts.size == MESSAGE_LINK_SEGMENTS && parts[RESOURCE_SEGMENT] == "chats" &&
            parts[ACTION_SEGMENT] == "messages" && parts[MESSAGE_ACTION_SEGMENT] == "context" &&
            method == HttpMethod.PUT
        ) {
            exclusions.update(parts[ID_SEGMENT], parts[MESSAGE_ID_SEGMENT], body)
            return ok()
        }
        if (parts.size == MESSAGE_LINK_SEGMENTS && parts[RESOURCE_SEGMENT] == "chats" &&
            parts[ACTION_SEGMENT] == "messages" && parts[MESSAGE_ACTION_SEGMENT] == "link" && method == HttpMethod.GET
        ) {
            return json.encodeToJsonElement(chats.link(parts[ID_SEGMENT], parts[MESSAGE_ID_SEGMENT]))
        }
        if (parts.size == ACTION_SEGMENTS && parts[RESOURCE_SEGMENT] == "preview" &&
            parts[ID_SEGMENT] == "jobs" && method == HttpMethod.GET
        ) {
            return json.encodeToJsonElement(drafts.job(parts[ACTION_SEGMENT]))
        }
        if (parts.size == ITEM_SEGMENTS && parts[RESOURCE_SEGMENT] == "uploads" && method == HttpMethod.DELETE) {
            uploads.delete(parts[ID_SEGMENT])
            return ok()
        }
        if (parts.size == ACTION_SEGMENTS && parts[RESOURCE_SEGMENT] == "stickers" &&
            parts[ID_SEGMENT] == "sets" && method == HttpMethod.GET
        ) {
            return json.encodeToJsonElement(attachments.stickerSet(parts[ACTION_SEGMENT]))
        }
        if (parts.size >= ITEM_SEGMENTS && parts[RESOURCE_SEGMENT] == "model-profiles") {
            val id = parts[ID_SEGMENT]
            return when {
                parts.size == ITEM_SEGMENTS && method == HttpMethod.PUT -> json.encodeToJsonElement(
                    models.save(id, body),
                )
                parts.size == ITEM_SEGMENTS && method == HttpMethod.DELETE -> {
                    models.delete(id)
                    ok()
                }
                parts.size == ACTION_SEGMENTS && parts[ACTION_SEGMENT] == "test" && method == HttpMethod.POST ->
                    json.encodeToJsonElement(models.test(id, body))
                else -> throw RouteNotFoundException()
            }
        }
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
                parts.size == ACTION_SEGMENTS && method == HttpMethod.PUT && parts[ACTION_SEGMENT] == "feedback" ->
                    json.encodeToJsonElement(activity.feedback(id, body))
                parts.size == ACTION_SEGMENTS && method == HttpMethod.DELETE && parts[ACTION_SEGMENT] == "feedback" -> {
                    activity.removeFeedback(id)
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
                parts[ACTION_SEGMENT] == "gallery" && method == HttpMethod.GET -> json.encodeToJsonElement(
                    chats.gallery(
                        id,
                        exchange.requestURI.query?.split('&')?.firstOrNull {
                            it.startsWith("around=")
                        }?.substringAfter('=').orEmpty(),
                    ),
                )
                parts[ACTION_SEGMENT] == "context-exclusions" && method == HttpMethod.DELETE -> {
                    exclusions.clear(id)
                    ok()
                }
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
                parts[ACTION_SEGMENT] == "attachments" && method == HttpMethod.POST -> {
                    attachments.send(id, body)
                    ok()
                }
                parts[ACTION_SEGMENT] == "stickers" && method == HttpMethod.POST -> {
                    attachments.sticker(id, body)
                    ok()
                }
                parts[ACTION_SEGMENT] == "rule" && method == HttpMethod.PUT -> {
                    chats.saveRule(id, body)
                    ok()
                }
                else -> throw RouteNotFoundException()
            }
        }

        throw RouteNotFoundException()
    }

    private fun ok(): JsonObject = buildJsonObject { put("ok", true) }
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        validation.close()
        modelClients.close()
        activity.close()
    }
}
