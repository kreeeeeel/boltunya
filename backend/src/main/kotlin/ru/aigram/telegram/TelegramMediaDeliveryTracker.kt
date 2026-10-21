package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.stereotype.Component
import ru.aigram.exception.impl.TelegramSendFailedException
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val RECEIPT_LIFETIME_MS = 5L * 60 * 1000
private const val MAX_EARLY_RECEIPTS = 1000

/** Keeps album files alive until TDLib confirms delivery; early updates may precede the send response. */
@Component
class TelegramMediaDeliveryTracker(private val clock: Clock) {
    private val receipts = ConcurrentHashMap<Triple<String, Long, Long>, MediaDeliveryReceipt>()

    fun succeeded(ownerId: String, chatId: Long, temporaryId: Long, finalId: Long) {
        receipt(Triple(ownerId, chatId, temporaryId)).result.complete(finalId)
    }

    fun failed(ownerId: String, chatId: Long, temporaryId: Long) {
        receipt(Triple(ownerId, chatId, temporaryId)).result.completeExceptionally(TelegramSendFailedException())
    }

    fun await(ownerId: String, messages: List<TdApi.Message>, timeout: Duration = Duration.ofMinutes(3)): List<String> =
        track(ownerId, messages).get(timeout.toMillis(), TimeUnit.MILLISECONDS)

    fun track(ownerId: String, messages: List<TdApi.Message>): CompletableFuture<List<String>> {
        if (messages.isEmpty() || messages.stream().anyMatch { it.sendingState is TdApi.MessageSendingStateFailed }) {
            throw TelegramSendFailedException()
        }
        val pending = messages.stream().filter { it.sendingState != null }.map {
            Triple(ownerId, it.chatId, it.id)
        }.toList()
        val results = pending.stream().map { receipt(it).result }.toList()
        return CompletableFuture.allOf(*results.toTypedArray()).thenApply {
            messages.stream().map { message ->
                val index = pending.indexOf(Triple(ownerId, message.chatId, message.id))
                if (index < 0) {
                    message.id.toString()
                } else {
                    results[index].join().toString()
                }
            }.toList()
        }.whenComplete { _, _ ->
            pending.stream().forEach { receipts.remove(it) }
        }
    }

    private fun receipt(key: Triple<String, Long, Long>): MediaDeliveryReceipt {
        receipts[key]?.let { return it }
        val now = clock.millis()
        receipts.entries.stream().filter { it.value.result.isDone && it.value.createdAt < now - RECEIPT_LIFETIME_MS }
            .forEach { receipts.remove(it.key, it.value) }
        if (receipts.size >= MAX_EARLY_RECEIPTS) {
            receipts.entries.stream().filter { it.value.result.isDone }.sorted(compareBy { it.value.createdAt })
                .limit((receipts.size - MAX_EARLY_RECEIPTS + 1).toLong()).forEach { receipts.remove(it.key, it.value) }
        }
        return receipts.computeIfAbsent(key) { MediaDeliveryReceipt(now) }
    }
}
