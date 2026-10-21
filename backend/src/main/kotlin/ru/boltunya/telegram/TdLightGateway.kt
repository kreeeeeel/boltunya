package ru.boltunya.telegram

import it.tdlight.Init
import it.tdlight.Log
import it.tdlight.Slf4JLogMessageHandler
import it.tdlight.client.APIToken
import it.tdlight.client.AuthenticationSupplier
import it.tdlight.client.SimpleTelegramClient
import it.tdlight.client.SimpleTelegramClientFactory
import it.tdlight.client.TDLibSettings
import it.tdlight.client.TelegramError
import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import ru.boltunya.domain.ChatGallery
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatSummary
import ru.boltunya.domain.MediaFile
import ru.boltunya.domain.OutgoingMedia
import ru.boltunya.domain.StickerCatalog
import ru.boltunya.domain.StickerItem
import ru.boltunya.domain.TelegramCredentials
import ru.boltunya.domain.TelegramStatus
import ru.boltunya.exception.IntegrationException
import ru.boltunya.exception.InvalidInputException
import ru.boltunya.exception.impl.AuthenticationFailedException
import ru.boltunya.exception.impl.ChatUnavailableException
import ru.boltunya.exception.impl.ConnectionAlreadyStartedException
import ru.boltunya.exception.impl.MediaTooLargeException
import ru.boltunya.exception.impl.MediaUnavailableException
import ru.boltunya.exception.impl.SavedSessionNotFoundException
import ru.boltunya.exception.impl.SessionRestoreException
import ru.boltunya.exception.impl.TelegramConnectionException
import ru.boltunya.exception.impl.TelegramNotConnectedException
import ru.boltunya.exception.impl.TelegramRateLimitException
import ru.boltunya.exception.impl.TelegramSendFailedException
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.CredentialStore
import ru.boltunya.storage.SettingsStore
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

private const val APPLICATION_VERSION = "2.3.0"
private const val MAX_FILE_BYTES = 100L * 1024 * 1024
private const val FILE_LOOKUP_TIMEOUT_SECONDS = 10L
private const val FILE_DOWNLOAD_TIMEOUT_SECONDS = 60L
private const val FILE_DOWNLOAD_PRIORITY = 16

class TdLightGateway @Inject constructor(
    private val directory: Path,
    private val settingsStore: SettingsStore,
    private val credentialsStore: CredentialStore,
    private val interaction: TelegramInteraction,
    private val mediaFiles: TelegramMediaStore,
    private val directoryView: TelegramChatDirectory,
    private val historyReader: TelegramHistoryReader,
    private val cache: TelegramCache,
    private val connection: TelegramConnectionState,
    private val updates: TelegramUpdateHandler,
    private val loader: TelegramChatLoader,
    private val sender: TelegramMessageSender,
    private val prefetch: MediaPrefetcher,
    private val mediaSender: TelegramMediaSender,
    private val stickerService: TelegramStickerService,
    private val galleries: TelegramGalleryService,
    private val links: TelegramMessageLinks,
) : TelegramGateway {
    private var factory: SimpleTelegramClientFactory? = null

    @Volatile private var client: SimpleTelegramClient? = null

    internal fun downloadFile(id: Int): TdApi.File {
        val active = readyClient()
        val file = active.send(TdApi.GetFile(id)).get(FILE_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val expected = mediaFiles.uniqueId(id)
        if (expected.isNotEmpty() && file.remote?.uniqueId != expected) throw MediaUnavailableException()
        if (maxOf(file.size, file.expectedSize) > MAX_FILE_BYTES) throw MediaTooLargeException()
        return active.send(
            TdApi.DownloadFile(id, FILE_DOWNLOAD_PRIORITY, 0, 0, true),
        ).get(FILE_DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    override fun status(): TelegramStatus {
        val photo = cache.users[cache.ownId]?.profilePhoto?.small
        return connection.status.copy(
            userId = cache.ownId.takeIf { it != 0L }?.toString().orEmpty(),
            name = cache.users[cache.ownId]?.let {
                (it.firstName + " " + it.lastName).trim()
            } ?: connection.status.name,
            avatarUrl = photo?.let {
                "/api/media/${mediaFiles.register(it, "image/jpeg", "avatar.jpg")}"
            }.orEmpty(),
        )
    }

    override fun media(fileId: Int): MediaFile = mediaFiles.get(fileId)
    override fun sendMedia(chatId: String, media: List<OutgoingMedia>, replyToId: String, topicId: Int): List<String> =
        mediaSender.send(chatId, media, replyToId, topicId)
    override fun stickers(): StickerCatalog = stickerService.catalog()
    override fun stickerSet(id: String): List<StickerItem> = stickerService.set(id)
    override fun gallery(chatId: String, around: String): ChatGallery = galleries.gallery(chatId, around)
    override fun messageLink(chatId: String, messageId: String): String = links.link(chatId, messageId)
    override fun sendSticker(chatId: String, stickerId: String, replyToId: String, topicId: Int): String =
        stickerService.send(chatId, stickerId, replyToId, topicId)

    @Synchronized
    override fun connect(credentials: TelegramCredentials) {
        if (client != null) {
            if (connection.status.state != "error") {
                throw ConnectionAlreadyStartedException()
            }
            connection.version++
            interaction.cancel()
            client?.closeAsync()?.get(15, TimeUnit.SECONDS)
            client = null
        }
        connection.status = TelegramStatus(state = "connecting")
        val version = ++connection.version
        try {
            Init.init()
            Log.setLogMessageHandler(0, Slf4JLogMessageHandler())
            val activeFactory = factory ?: SimpleTelegramClientFactory()
            factory = activeFactory
            val settings = TDLibSettings.create(APIToken(credentials.apiId, credentials.apiHash))
            settings.databaseDirectoryPath = directory.resolve("telegram/data")
            settings.downloadedFilesDirectoryPath = directory.resolve("telegram/files")
            settings.systemLanguageCode = "ru"
            settings.deviceModel = "Болтунья Desktop"
            settings.applicationVersion = APPLICATION_VERSION
            settings.isFileDatabaseEnabled = true
            settings.isChatInfoDatabaseEnabled = true
            settings.isMessageDatabaseEnabled = true
            val builder = activeFactory.builder(settings)
            builder.setClientInteraction(interaction)
            updates.register(builder, version)
            val activeClient = builder.build(AuthenticationSupplier.user(credentials.phone))
            client = activeClient
            activeClient.meAsync.whenCompleteAsync { me, error ->
                if (client !== activeClient) return@whenCompleteAsync
                if (error == null) {
                    cache.ownId = me.id
                    cache.users[me.id] = me
                    connection.status = TelegramStatus(state = "ready", name = "${me.firstName} ${me.lastName}".trim())
                    loader.load(activeClient, TdApi.ChatListMain(), version)
                    loader.load(activeClient, TdApi.ChatListArchive(), version)
                } else {
                    connection.status =
                        TelegramStatus(
                            state = "error",
                            error = AuthenticationFailedException().message,
                        )
                }
            }
            credentialsStore.save(credentials)
        } catch (exception: Exception) {
            connection.status =
                TelegramStatus(
                    state = "error",
                    error = TelegramConnectionException().message,
                )
            throw TelegramConnectionException(exception)
        }
    }

    @Synchronized
    override fun restore() {
        if (connection.status.state == "ready") return
        try {
            val saved = credentialsStore.load()
                ?: throw SavedSessionNotFoundException()
            connection.version++
            interaction.cancel()
            client?.closeAsync()?.get(15, TimeUnit.SECONDS)
            client = null
            connect(saved)
        } catch (exception: Exception) {
            connection.status = TelegramStatus(
                state = "error",
                error = SessionRestoreException().message,
            )
            throw SessionRestoreException(exception)
        }
    }

    override fun submit(value: String) {
        interaction.submit(value)
        connection.status = connection.status.copy(error = "")
    }

    override fun chats(): List<ChatSummary> = directoryView.list()
    override fun history(chatId: String, limit: Int, before: Long): List<ChatMessage> =
        historyReader.read(readyClient(), parseChatId(chatId), limit, before)
    override fun send(chatId: String, text: String, replyToId: String, topicId: Int): String =
        sender.send(chatId, text, replyToId, topicId)
    override fun typing(chatId: String, active: Boolean) {
        sender.typing(chatId, active)
    }

    internal fun readyClient(): SimpleTelegramClient {
        val active = client
        if (active == null || connection.status.state != "ready") {
            throw TelegramNotConnectedException()
        }
        return active
    }

    internal fun parseChatId(value: String): Long {
        val id = value.toLongOrNull()
        if (id == null || cache.chats[id] == null) {
            throw ChatUnavailableException()
        }
        return id
    }

    @Synchronized
    override fun logout() {
        prefetch.cancelPending()
        connection.version++
        settingsStore.update { it.copy(paused = true) }
        interaction.cancel()
        if (connection.status.state == "ready") {
            client?.send(TdApi.LogOut())?.get(20, TimeUnit.SECONDS)
        } else {
            client?.sendClose()
        }
        client = null
        credentialsStore.delete()
        cache.clear()
        mediaFiles.clear()
        connection.status = TelegramStatus()
    }

    override fun close() {
        connection.version++
        interaction.cancel()
        try {
            client?.closeAsync()?.get(15, TimeUnit.SECONDS)
        } catch (exception: Exception) {
            System.err.println("Telegram shutdown did not finish: ${exception.javaClass.simpleName}")
        }
        factory?.close()
    }
}
