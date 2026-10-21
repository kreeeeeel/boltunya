package ru.aigram.configuration

import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.aigram.storage.CredentialStore
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.MediaDownloadPolicy
import ru.aigram.telegram.MediaPrefetcher
import ru.aigram.telegram.TdLightGateway
import ru.aigram.telegram.TelegramCache
import ru.aigram.telegram.TelegramChatDirectory
import ru.aigram.telegram.TelegramChatLoader
import ru.aigram.telegram.TelegramConnectionState
import ru.aigram.telegram.TelegramGalleryService
import ru.aigram.telegram.TelegramHistoryReader
import ru.aigram.telegram.TelegramInteraction
import ru.aigram.telegram.TelegramMediaSender
import ru.aigram.telegram.TelegramMediaStore
import ru.aigram.telegram.TelegramMessageDeletion
import ru.aigram.telegram.TelegramMessageLinks
import ru.aigram.telegram.TelegramMessageSender
import ru.aigram.telegram.TelegramStickerService
import ru.aigram.telegram.TelegramUpdateHandler
import java.nio.file.Path

@Configuration(proxyBeanMethods = false)
class TelegramConfiguration {
    @Bean
    fun telegramMediaStore(directory: Path, gateway: ObjectProvider<TdLightGateway>): TelegramMediaStore =
        TelegramMediaStore(
            directory.resolve("telegram/files"),
            listOf("profile_photos", "stickers", "thumbnails", "wallpapers").stream().map {
                directory.resolve("telegram/data/$it")
            }.toList(),
        ) { gateway.getObject().downloadFile(it) }

    @Bean
    fun telegramInteraction(connection: TelegramConnectionState): TelegramInteraction = TelegramInteraction {
        connection.status = it
    }

    @Bean
    fun mediaPrefetcher(files: TelegramMediaStore, policy: MediaDownloadPolicy): MediaPrefetcher =
        MediaPrefetcher(files, policy)

    @Bean
    fun tdLightGateway(
        directory: Path,
        settings: SettingsStore,
        credentials: CredentialStore,
        interaction: TelegramInteraction,
        media: TelegramMediaStore,
        chats: TelegramChatDirectory,
        history: TelegramHistoryReader,
        cache: TelegramCache,
        connection: TelegramConnectionState,
        updates: TelegramUpdateHandler,
        loader: TelegramChatLoader,
        sender: TelegramMessageSender,
        prefetch: MediaPrefetcher,
        mediaSender: TelegramMediaSender,
        stickers: TelegramStickerService,
        galleries: TelegramGalleryService,
        links: TelegramMessageLinks,
        deletion: TelegramMessageDeletion,
    ): TdLightGateway = TdLightGateway(
        directory, settings, credentials, interaction, media, chats, history, cache, connection, updates,
        loader, sender, prefetch, mediaSender, stickers, galleries, links, deletion,
    )
}
