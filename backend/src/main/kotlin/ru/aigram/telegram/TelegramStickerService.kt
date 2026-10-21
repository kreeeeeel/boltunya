package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import ru.aigram.domain.media.StickerCatalog
import ru.aigram.domain.media.StickerItem
import ru.aigram.exception.impl.InvalidMediaIdentifierException
import ru.aigram.exception.impl.TelegramSendFailedException
import ru.aigram.exception.impl.UnknownMediaException
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val STICKER_TIMEOUT_SECONDS = 15L
private const val STICKER_CACHE_MS = 30_000L

@Component
class TelegramStickerService(
    private val gateway: ObjectProvider<TdLightGateway>,
    private val registry: TelegramStickerRegistry,
    private val clock: Clock,
    private val automatedSends: TelegramAutomatedSendRegistry,
) {
    private var cached: StickerCatalog? = null
    private var ownerId = ""
    private var refreshedAt = Long.MIN_VALUE

    @Synchronized
    fun catalog(): StickerCatalog {
        val service = activate()
        val now = clock.millis()
        val previous = cached
        if (previous != null && now - refreshedAt < STICKER_CACHE_MS) {
            return previous
        }
        val client = service.readyClient()
        return request {
            val recent = client.send(TdApi.GetRecentStickers(false))
            val favorites = client.send(TdApi.GetFavoriteStickers())
            val installed = client.send(TdApi.GetInstalledStickerSets(TdApi.StickerTypeRegular()))
            CompletableFuture.allOf(recent, favorites, installed).get(STICKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val result = StickerCatalog(
                items(recent.join().stickers),
                items(favorites.join().stickers),
                installed.join().sets.orEmpty().asList().stream().map(registry::remember).toList(),
            )
            cached = result
            refreshedAt = now
            result
        }
    }

    @Synchronized
    fun set(id: String): List<StickerItem> {
        val service = activate()
        val number = id.toLongOrNull()?.takeIf { it > 0 }

        if (number == null) {
            throw InvalidMediaIdentifierException()
        }

        if (!registry.allowsSet(number)) {
            catalog()
            if (!registry.allowsSet(number)) {
                throw UnknownMediaException()
            }
        }
        return request {
            items(
                service.readyClient().send(
                    TdApi.GetStickerSet(number),
                ).get(STICKER_TIMEOUT_SECONDS, TimeUnit.SECONDS).stickers,
            )
        }
    }

    @Synchronized
    fun resolve(id: String): StickerItem {
        activate()
        return registry.resolve(id)
    }

    @Synchronized
    fun send(
        chatId: String,
        stickerId: String,
        replyToId: String = "",
        topicId: Int = 0,
        automated: Boolean = false,
    ): String {
        val service = activate()
        val input = TdApi.SendMessage().apply {
            this.chatId = service.parseChatId(chatId)
            if (replyToId.isNotEmpty()) {
                val replyId = replyToId.toLongOrNull()
                if (replyId == null || replyId <= 0) {
                    throw InvalidMediaIdentifierException()
                }

                replyTo = TdApi.InputMessageReplyToMessage(
                    replyId,
                    null,
                    0,
                    "",
                )
            }
            if (topicId != 0) {
                this.topicId = TdApi.MessageTopicForum(topicId)
            }
            inputMessageContent = registry.content(stickerId)
        }
        val client = service.readyClient()
        val send = if (automated) {
            automatedSends.begin(input.chatId)
        } else {
            null
        }
        if (send != null) {
            input.options = TdApi.MessageSendOptions().apply { sendingId = send.sendingId }
        }
        return request {
            try {
                val message = client.sendMessage(input, true).get(STICKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (send != null) {
                    automatedSends.completed(send, message)
                }
                message.id.toString()
            } catch (exception: ExecutionException) {
                if (send != null) {
                    automatedSends.rejected(send)
                }
                throw exception
            }
        }
    }

    private fun activate(): TdLightGateway {
        val service = gateway.getObject()
        service.readyClient()
        val owner = service.status().userId
        if (ownerId != owner) {
            registry.activate(owner)
            ownerId = owner
            cached = null
            refreshedAt = Long.MIN_VALUE
        }
        return service
    }

    private fun items(stickers: Array<TdApi.Sticker>?): List<StickerItem> = stickers.orEmpty().asList().stream()
        .map(registry::remember).filter { it != null }.map { it as StickerItem }.toList()

    private fun <T> request(action: () -> T): T = try {
        action()
    } catch (exception: ExecutionException) {
        throw TelegramSendFailedException(exception)
    } catch (exception: TimeoutException) {
        throw TelegramSendFailedException(exception)
    } catch (exception: InterruptedException) {
        Thread.currentThread().interrupt()
        throw TelegramSendFailedException(exception)
    }
}
