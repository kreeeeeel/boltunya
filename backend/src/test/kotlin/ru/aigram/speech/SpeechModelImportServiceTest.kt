package ru.aigram.speech

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.media.OutgoingMediaKind
import ru.aigram.domain.speech.SpeechAssetKind
import ru.aigram.exception.impl.InvalidSpeechModelImportException
import ru.aigram.exception.impl.SpeechImportedModelUnavailableException
import ru.aigram.exception.impl.UploadNotFoundException
import ru.aigram.media.UploadStore
import ru.aigram.resources.ResourceCatalog
import ru.aigram.speech.reply.VoiceTestGateway
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechModelImportServiceTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `import persists a selectable local voice without exposing paths or invoking downloads`() {
        val registry = SpeechCustomModelRegistry(directory, Json)
        val uploads = UploadStore(directory, Clock.systemUTC())
        val archive = speechImportArchive()
        val upload = uploads.create(
            "100",
            "voice.tar.bz2",
            "application/octet-stream",
            ByteArrayInputStream(archive),
            archive.size.toLong(),
        )
        val downloads = AtomicInteger()
        val downloader = SpeechDownloadClient { _, _, _ -> downloads.incrementAndGet() }
        speechCatalog(directory, downloader, registry).use { catalog ->
            val service = service(uploads, registry, catalog)
            val state = service.importModel("100", upload.id, "My local voice", "RU", "Author conditions")
            val voice = state.assets.single { it.userProvided }
            assertTrue(voice.id.startsWith("custom-"))
            assertTrue(voice.installed)
            assertEquals(SpeechAssetKind.SYNTHESIS, voice.kind)
            assertEquals(listOf("ru"), voice.languages)
            assertEquals("", voice.sourceUrl)
            assertEquals("Author conditions", voice.license)
            assertFalse(Json.encodeToString(state).contains(directory.toString()))
            assertFailsWith<UploadNotFoundException> { uploads.media("100", upload.id, OutgoingMediaKind.DOCUMENT, "") }
            catalog.install(voice.id)
            assertEquals(0, downloads.get())
            installSpeechFixture(directory, "sherpa-runtime")
            assertEquals("model.onnx", catalog.model(voice.id, SpeechAssetKind.SYNTHESIS).modelFile)
            speechCatalog(directory, SpeechDownloadClient { _, _, _ -> downloads.incrementAndGet() }).use { reopened ->
                assertEquals(voice.id, reopened.list().single { it.userProvided }.id)
                assertEquals("model.onnx", reopened.model(voice.id, SpeechAssetKind.SYNTHESIS).modelFile)
                Files.delete(reopened.location(voice.id).resolve("tokens.txt"))
                assertFalse(reopened.list().single { it.userProvided }.installed)
                assertFailsWith<SpeechImportedModelUnavailableException> { reopened.install(voice.id) }
                assertEquals(0, downloads.get())
            }
        }
    }

    @Test
    fun `invalid or cancelled imports preserve the lease for retry and publish no partial model`() {
        val registry = SpeechCustomModelRegistry(directory, Json)
        val uploads = UploadStore(directory, Clock.systemUTC())
        val bytes = byteArrayOf(1, 2, 3)
        val upload = uploads.create(
            "100",
            "invalid.tar.bz2",
            "application/octet-stream",
            ByteArrayInputStream(bytes),
            bytes.size.toLong(),
        )
        speechCatalog(directory, SpeechDownloadClient { _, _, _ -> }).use { catalog ->
            val service = service(uploads, registry, catalog)
            assertFailsWith<InvalidSpeechModelImportException> {
                service.importModel("100", upload.id, "Invalid", "", "")
            }
            assertTrue(Files.exists(uploads.media("100", upload.id, OutgoingMediaKind.DOCUMENT, "").path))
            val archive = speechImportArchive()
            val valid = uploads.create(
                "100",
                "voice.tar.bz2",
                "application/octet-stream",
                ByteArrayInputStream(archive),
                archive.size.toLong(),
            )
            try {
                Thread.currentThread().interrupt()
                assertFailsWith<CancellationException> {
                    service.importModel("100", valid.id, "Cancelled", "", "")
                }
            } finally {
                Thread.interrupted()
            }
            assertTrue(Files.exists(uploads.media("100", valid.id, OutgoingMediaKind.DOCUMENT, "").path))
            assertTrue(registry.list().isEmpty())
            Files.list(directory.resolve("speech")).use { files -> assertEquals(0L, files.count()) }
        }
    }

    @Test
    fun `an upload owned by another account cannot be imported`() {
        val registry = SpeechCustomModelRegistry(directory, Json)
        val uploads = UploadStore(directory, Clock.systemUTC())
        val bytes = speechImportArchive()
        val upload = uploads.create(
            "200",
            "voice.tar.bz2",
            "application/octet-stream",
            ByteArrayInputStream(bytes),
            bytes.size.toLong(),
        )
        speechCatalog(directory, SpeechDownloadClient { _, _, _ -> }, registry).use { catalog ->
            assertFailsWith<UploadNotFoundException> {
                service(uploads, registry, catalog).importModel("100", upload.id, "Wrong account", "", "")
            }
            assertTrue(registry.list().isEmpty())
        }
    }

    @Test
    fun `switching account during extraction cannot publish an old account upload`() {
        val registry = SpeechCustomModelRegistry(directory, Json)
        val uploads = UploadStore(directory, Clock.systemUTC())
        val bytes = speechImportArchive()
        val upload = uploads.create(
            "100",
            "voice.tar.bz2",
            "application/octet-stream",
            ByteArrayInputStream(bytes),
            bytes.size.toLong(),
        )
        val gateway = VoiceTestGateway()
        speechCatalog(directory, SpeechDownloadClient { _, _, _ -> }, registry).use { catalog ->
            val service = service(uploads, registry, catalog, gateway, OwnerSwitchingSpeechImporter(gateway))

            assertFailsWith<InvalidSpeechModelImportException> {
                service.importModel("100", upload.id, "Old account voice", "", "")
            }

            assertTrue(registry.list().isEmpty())
            assertTrue(Files.exists(uploads.media("100", upload.id, OutgoingMediaKind.DOCUMENT, "").path))
            Files.list(directory.resolve("speech")).use { files -> assertEquals(0L, files.count()) }
        }
    }

    private fun service(
        uploads: UploadStore,
        registry: SpeechCustomModelRegistry,
        catalog: SpeechAssetCatalog,
        gateway: VoiceTestGateway = VoiceTestGateway(),
        archives: SpeechModelArchiveImporter = SpeechModelArchiveImporter(),
    ): SpeechModelImportService = SpeechModelImportService(
        directory,
        uploads,
        archives,
        registry,
        catalog,
        SpeechLicenseInstaller(Json),
        ResourceCatalog(),
        gateway,
    )
}
