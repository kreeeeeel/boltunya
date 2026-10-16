package ru.boltunya.telegram

import jakarta.inject.Inject
import ru.boltunya.domain.ChatMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val MAX_PENDING_DOWNLOADS = 64

class MediaPrefetcher @Inject constructor(
    private val files: TelegramMediaStore,
    private val policy: MediaDownloadPolicy,
) : AutoCloseable {
    private val pending = ConcurrentLinkedQueue<ChatMessage>()
    private val queued = ConcurrentHashMap.newKeySet<Int>()
    private val executor = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "media-prefetch").also { thread -> thread.isDaemon = true }
    }

    fun start() {
        executor.scheduleWithFixedDelay({ downloadOne() }, 0, 1, TimeUnit.SECONDS)
    }

    fun observe(message: ChatMessage) {
        val media = message.media ?: return
        if (!policy.allows(message.kind, media.size) || files.isDownloaded(media.fileId)) return
        if (pending.size >= MAX_PENDING_DOWNLOADS) return
        if (queued.add(media.fileId)) pending.add(message)
    }

    internal fun downloadOne() {
        val message = pending.poll() ?: return
        val media = message.media ?: return
        try {
            if (policy.allows(message.kind, media.size)) files.get(media.fileId)
        } catch (exception: Exception) {
            System.err.println("Media prefetch failed: ${exception.javaClass.simpleName}")
        } finally {
            queued.remove(media.fileId)
        }
    }

    fun cancelPending() {
        pending.clear()
        queued.clear()
    }

    override fun close() {
        cancelPending()
        executor.shutdownNow()
    }
}
