package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.storage.SettingsStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramCacheStorageTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `reports cache categories and optimizes only TDLib file storage with all observed types`() {
        val current = TdApi.StorageStatistics().apply {
            size = 600
            count = 3
            byChat =
                arrayOf(
                    TdApi.StorageStatisticsByChat().apply {
                        byFileType =
                            arrayOf(
                                TdApi.StorageStatisticsByFileType(TdApi.FileTypeProfilePhoto(), 100, 1),
                                TdApi.StorageStatisticsByFileType(TdApi.FileTypeVideo(), 200, 1),
                                TdApi.StorageStatisticsByFileType(TdApi.FileTypeDocument(), 300, 1),
                            )
                    },
                )
        }
        val requests = mutableListOf<TdApi.Function<TdApi.StorageStatistics>>()
        val client =
            StorageClient { request ->
                requests.add(request)
                if (request is TdApi.OptimizeStorage) {
                    TdApi.StorageStatistics(0, 0, emptyArray())
                } else {
                    current
                }
            }
        val settings = Files.writeString(directory.resolve("settings.json"), "{}")
        val retained = Files.writeString(directory.resolve("saved-history.json"), "original text")
        val files = TelegramMediaStore(directory) { error("No download") }
        files.update(
            TdApi.File().apply {
                id = 1
                local = TdApi.LocalFile().apply { isDownloadingCompleted = true }
            },
        )
        MediaPrefetcher(files, MediaDownloadPolicy(SettingsStore(directory))).use { prefetch ->
            val service = TelegramCacheStorage(
                client,
                files,
                prefetch,
                TelegramOutgoingFileStore(directory, Clock.systemUTC()),
            )
            val stats = service.statistics()
            assertEquals(600L, stats.totalBytes)
            assertEquals(100L, stats.photoBytes)
            assertEquals(200L, stats.videoBytes)
            assertEquals(300L, stats.documentBytes)
            assertEquals(0L, service.clear().totalBytes)
            val optimize = requests.filterIsInstance<TdApi.OptimizeStorage>().single()
            assertEquals(0L, optimize.size)
            assertEquals(0, optimize.immunityDelay)
            assertTrue(optimize.fileTypes.any { it is TdApi.FileTypeProfilePhoto })
            assertTrue(optimize.chatIds.isEmpty())
            assertTrue(optimize.excludeChatIds.isEmpty())
            assertFalse(optimize.returnDeletedFileStatistics)
            assertEquals("{}", Files.readString(settings))
            assertEquals("original text", Files.readString(retained))
            assertFalse(files.isDownloaded(1))
        }
    }

    @Test
    fun `reports owned outgoing payloads without sidecars and clears only completed copies`() {
        val outgoing = TelegramOutgoingFileStore(directory, Clock.systemUTC())
        val source = Files.writeString(directory.resolve("voice.ogg"), "OggS synthetic")
        val active = outgoing.retain(source)
        val finished = outgoing.retain(source)
        outgoing.completed(finished)
        val files = TelegramMediaStore(directory) { error("No download") }
        val client = StorageClient { TdApi.StorageStatistics(0, 0, emptyArray()) }
        MediaPrefetcher(files, MediaDownloadPolicy(SettingsStore(directory))).use { prefetch ->
            val service = TelegramCacheStorage(client, files, prefetch, outgoing)
            val expectedBytes = Files.size(source)
            assertEquals(expectedBytes * 2, service.statistics().totalBytes)
            assertEquals(2L, service.statistics().fileCount)
            val remaining = service.clear()
            assertEquals(expectedBytes, remaining.totalBytes)
            assertEquals(expectedBytes, remaining.documentBytes)
            assertEquals(1L, remaining.fileCount)
            assertTrue(Files.exists(active))
            assertFalse(Files.exists(finished))
        }
    }
}
