package ru.boltunya.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import jakarta.inject.Provider
import ru.boltunya.domain.ChatGallery
import ru.boltunya.exception.impl.InvalidMediaIdentifierException
import ru.boltunya.exception.impl.MediaUnavailableException
import ru.boltunya.exception.impl.TelegramSendFailedException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.stream.Collectors

private const val GALLERY_PAGE_SIZE = 60
private const val GALLERY_TIMEOUT_SECONDS = 20L

class TelegramGalleryService @Inject constructor(
    private val gateway: Provider<TdLightGateway>,
    private val view: TelegramMessageView,
) {
    fun gallery(chatId: String, around: String): ChatGallery {
        val service = gateway.get()
        val chat = service.parseChatId(chatId)
        val message = around.toLongOrNull()?.takeIf { it > 0 } ?: throw InvalidMediaIdentifierException()
        val client = service.readyClient()
        return try {
            val selected = client.send(TdApi.GetMessage(chat, message))
            val page = client.send(search(chat, message))
            val latest = client.send(
                search(chat, 0).apply {
                    limit = 1
                    offset = 0
                },
            )
            CompletableFuture.allOf(selected, page, latest).get(GALLERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            combine(chat, page.join(), selected.join(), latest.join())
        } catch (exception: ExecutionException) {
            throw TelegramSendFailedException(exception)
        } catch (exception: TimeoutException) {
            throw TelegramSendFailedException(exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TelegramSendFailedException(exception)
        }
    }

    internal fun search(chatId: Long, messageId: Long): TdApi.SearchChatMessages = TdApi.SearchChatMessages().apply {
        this.chatId = chatId
        fromMessageId = messageId
        query = ""
        offset = -GALLERY_PAGE_SIZE / 2
        limit = GALLERY_PAGE_SIZE
        filter = TdApi.SearchMessagesFilterPhotoAndVideo()
    }

    internal fun combine(
        chatId: Long,
        page: TdApi.FoundChatMessages,
        selected: TdApi.Message,
        latest: TdApi.FoundChatMessages,
    ): ChatGallery {
        if (selected.chatId != chatId || !visual(selected)) throw MediaUnavailableException()
        val messages = (page.messages.orEmpty().asList() + selected).stream()
            .filter { it.chatId == chatId && visual(it) }
            .collect(Collectors.toMap({ it.id }, { it }, { first, _ -> first })).values.stream()
            .sorted(compareBy { it.id }).map(view::map).toList()
        val newest = messages.lastOrNull()?.id?.toLongOrNull() ?: 0
        val moreAfter = latest.messages.orEmpty().asList().stream().anyMatch { it.chatId == chatId && it.id > newest }
        return ChatGallery(messages, page.nextFromMessageId != 0L, moreAfter)
    }

    private fun visual(message: TdApi.Message): Boolean = message.content is TdApi.MessagePhoto ||
        message.content is TdApi.MessageVideo || message.content is TdApi.MessageAnimation ||
        message.content is TdApi.MessageVideoNote
}
