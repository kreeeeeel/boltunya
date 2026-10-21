package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.stereotype.Component
import ru.aigram.domain.telegram.TelegramAutomatedSend
import ru.aigram.exception.impl.TelegramNotConnectedException
import ru.aigram.exception.impl.TelegramSendFailedException
import ru.aigram.storage.AutomatedReplyStore
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream

private const val MAX_TRACKED_SENDS = 4096
private const val SEND_RETENTION_MILLIS = 60L * 60 * 1000

@Component
class TelegramAutomatedSendRegistry(
    private val connection: TelegramConnectionState,
    private val cache: TelegramCache,
    private val automated: AutomatedReplyStore,
    private val clock: Clock,
) {
    private val sequence = AtomicInteger()
    private val sends = LinkedHashMap<Int, TelegramAutomatedSend>()
    private val messages = HashMap<Pair<Long, Long>, Int>()

    @Synchronized
    fun begin(chatId: Long): TelegramAutomatedSend {
        prune()
        if (cache.ownId == 0L) {
            throw TelegramNotConnectedException()
        }
        if (sends.size >= MAX_TRACKED_SENDS) {
            throw TelegramSendFailedException()
        }
        val id = Stream.generate {
            sequence.updateAndGet { previous ->
                if (previous == Int.MAX_VALUE) {
                    1
                } else {
                    previous + 1
                }
            }
        }.limit(MAX_TRACKED_SENDS + 1L).filter { !sends.containsKey(it) }.findFirst().orElse(null)
        if (id == null) {
            throw TelegramSendFailedException()
        }
        val send = TelegramAutomatedSend(id, cache.ownId.toString(), connection.version, chatId, clock.millis())
        sends[id] = send
        return send
    }

    @Synchronized
    fun pending(message: TdApi.Message): Boolean {
        prune()
        val state = message.sendingState as? TdApi.MessageSendingStatePending
        val send = state?.let { sends[it.sendingId] } ?: return false
        if (!matches(send, message)) {
            return false
        }
        messages[message.chatId to message.id] = send.sendingId
        return true
    }

    @Synchronized
    fun succeeded(oldMessageId: Long, message: TdApi.Message): Boolean {
        prune()
        val id = messages[message.chatId to oldMessageId] ?: messages[message.chatId to message.id]
        val send = id?.let(sends::get) ?: return false
        if (!matches(send, message)) {
            return false
        }
        messages[message.chatId to message.id] = send.sendingId
        record(message)
        return true
    }

    @Synchronized
    fun completed(send: TelegramAutomatedSend, message: TdApi.Message) {
        prune()
        if (sends[send.sendingId] != send || !matches(send, message)) {
            return
        }
        messages[message.chatId to message.id] = send.sendingId
        record(message)
    }

    @Synchronized
    fun failed(chatId: Long, oldMessageId: Long) {
        val id = messages[chatId to oldMessageId]
        if (id != null) {
            sends.remove(id)
            messages.entries.removeIf { it.value == id }
        }
    }

    @Synchronized
    fun rejected(send: TelegramAutomatedSend) {
        if (sends[send.sendingId] == send) {
            sends.remove(send.sendingId)
            messages.entries.removeIf { it.value == send.sendingId }
        }
    }

    @Synchronized
    fun clear() {
        sends.clear()
        messages.clear()
    }

    @Synchronized
    fun known(message: TdApi.Message): Boolean {
        prune()
        val send = messages[message.chatId to message.id]?.let(sends::get)
        return matches(send, message)
    }

    private fun matches(send: TelegramAutomatedSend?, message: TdApi.Message): Boolean =
        send != null && message.isOutgoing && send.chatId == message.chatId &&
            send.ownerId == cache.ownId.toString() && send.version == connection.version

    private fun record(message: TdApi.Message) {
        try {
            automated.record(message.chatId.toString(), message.id.toString())
        } catch (exception: Exception) {
            System.err.println("Automated message marker failed: ${exception.javaClass.simpleName}")
        }
    }

    private fun prune() {
        val now = clock.millis()
        sends.entries.removeIf {
            it.value.version != connection.version || it.value.ownerId != cache.ownId.toString() ||
                now - it.value.createdAt > SEND_RETENTION_MILLIS
        }
        messages.entries.removeIf { !sends.containsKey(it.value) }
    }
}
