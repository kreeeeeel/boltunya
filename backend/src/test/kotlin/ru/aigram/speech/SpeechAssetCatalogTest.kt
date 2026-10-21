package ru.aigram.speech

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.speech.SpeechDownloadPhase
import ru.aigram.exception.impl.InvalidSpeechAssetException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechAssetCatalogTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `startup is offline and installation remains visible after recreating the catalog`() {
        val count = AtomicInteger()
        val download = SpeechDownloadClient { _, target, progress ->
            count.incrementAndGet()
            Files.write(target, byteArrayOf(1, 2, 3))
            progress(3)
        }
        speechCatalog(directory, download).use { catalog ->
            assertEquals(5, catalog.list().size)
            assertTrue(catalog.list().none { it.installed })
            assertEquals(0, count.get())
            assertFalse(Files.exists(directory.resolve("speech")))

            catalog.install("sherpa-runtime")
            awaitSpeech { catalog.state().downloads.any { it.phase == SpeechDownloadPhase.READY } }
            assertTrue(catalog.list().first { it.id == "sherpa-runtime" }.installed)
            assertTrue(Files.isRegularFile(directory.resolve("speech/sherpa-runtime/legal/manifest.json")))
            catalog.install("sherpa-runtime")
            assertEquals(1, count.get())
        }
        speechCatalog(directory, download).use { catalog ->
            assertTrue(catalog.list().first { it.id == "sherpa-runtime" }.installed)
            assertEquals(1, count.get())
        }
    }

    @Test
    fun `cancellation keeps no partial installation and never reports private error details`() {
        val entered = CountDownLatch(1)
        val download = SpeechDownloadClient { _, target, _ ->
            Files.write(target, byteArrayOf(1))
            entered.countDown()
            CountDownLatch(1).await(30, TimeUnit.SECONDS)
        }
        speechCatalog(directory, download).use { catalog ->
            catalog.install("sherpa-runtime")
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            catalog.cancel("sherpa-runtime")
            assertEquals(SpeechDownloadPhase.CANCELLED, catalog.state().downloads.single().phase)
            assertFalse(catalog.list().first().installed)
            assertEquals("", catalog.state().downloads.single().error)
            awaitSpeech {
                Files.list(directory.resolve("speech")).use { files -> files.findAny().isEmpty }
            }
        }
    }

    @Test
    fun `only curated identifiers are accepted and failure leaves previous installed assets intact`() {
        installSpeechFixture(directory, "sherpa-runtime")
        val download = SpeechDownloadClient { _, _, _ -> throw java.io.IOException("private path and transcript") }
        speechCatalog(directory, download).use { catalog ->
            assertFailsWith<InvalidSpeechAssetException> { catalog.install("../../outside") }
            catalog.install("piper-ru-irina")
            awaitSpeech { catalog.state().downloads.any { it.phase == SpeechDownloadPhase.ERROR } }
            assertEquals("SPEECH_INSTALL_FAILED", catalog.state().downloads.single().error)
            assertTrue(catalog.list().first { it.id == "sherpa-runtime" }.installed)
            assertFalse(catalog.list().first { it.id == "piper-ru-irina" }.installed)
        }
    }
}
