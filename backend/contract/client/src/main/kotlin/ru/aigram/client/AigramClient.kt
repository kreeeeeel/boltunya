package ru.aigram.client

import org.springframework.web.reactive.function.client.WebClient
import ru.aigram.client.generated.api.ActivityClientGenerated
import ru.aigram.client.generated.api.ChatsClientGenerated
import ru.aigram.client.generated.api.MediaClientGenerated
import ru.aigram.client.generated.api.MemoryClientGenerated
import ru.aigram.client.generated.api.ModelsClientGenerated
import ru.aigram.client.generated.api.SettingsClientGenerated
import ru.aigram.client.generated.api.SpeechClientGenerated
import ru.aigram.client.generated.api.SystemClientGenerated
import ru.aigram.client.generated.api.TelegramClientGenerated
import java.net.http.HttpClient
import java.util.concurrent.atomic.AtomicBoolean

/** Typed local API groups share one connection configuration and resolve session credentials per request. */
class AigramClient internal constructor(client: WebClient, private val transport: HttpClient) : AutoCloseable {
    val activity = ActivityClientGenerated(client)
    val chats = ChatsClientGenerated(client)
    val media = MediaClientGenerated(client)
    val memory = MemoryClientGenerated(client)
    val models = ModelsClientGenerated(client)
    val settings = SettingsClientGenerated(client)
    val speech = SpeechClientGenerated(client)
    val system = SystemClientGenerated(client)
    val telegram = TelegramClientGenerated(client)
    private val closed = AtomicBoolean()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            transport.close()
        }
    }
}
