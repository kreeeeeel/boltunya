package ru.aigram.speech

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import ru.aigram.domain.speech.SpeechAssetKind
import ru.aigram.domain.speech.SpeechDownloadPhase
import ru.aigram.exception.impl.InvalidSpeechAssetException
import ru.aigram.resources.ResourceCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechDependencyInstallTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `installing a voice downloads missing dependencies first and publishes only complete assets`() {
        val order = CopyOnWriteArrayList<String>()
        val downloader = SpeechDownloadClient { definition, target, _ ->
            order.add(definition.asset.id)
            Files.write(target, byteArrayOf(1))
        }
        catalog(downloader).use { catalog ->
            catalog.install("piper-ru-irina")
            awaitSpeech { catalog.list().first { it.id == "piper-ru-irina" }.installed }

            assertEquals(listOf("sherpa-runtime", "piper-ru-irina"), order.toList())
            assertTrue(catalog.list().first { it.id == "sherpa-runtime" }.installed)
            assertEquals(2, catalog.state().downloads.count { it.phase == SpeechDownloadPhase.READY })
        }
    }

    @Test
    fun `dependency failure terminates the parent without downloading its unusable weights`() {
        val calls = AtomicInteger()
        catalog(
            SpeechDownloadClient { _, _, _ ->
                calls.incrementAndGet()
                throw java.io.IOException("synthetic failure")
            },
        ).use { catalog ->
            catalog.install("piper-ru-irina")
            awaitSpeech { catalog.state().downloads.count { it.phase == SpeechDownloadPhase.ERROR } == 2 }

            assertEquals(1, calls.get())
            assertTrue(catalog.state().downloads.none { it.phase == SpeechDownloadPhase.QUEUED })
            assertTrue(catalog.list().none { it.installed })
        }
    }

    @Test
    fun `cancelling one voice does not interrupt a shared dependency requested by another voice`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val order = CopyOnWriteArrayList<String>()
        val downloader = SpeechDownloadClient { definition, target, _ ->
            order.add(definition.asset.id)
            if (definition.asset.id == "sherpa-runtime") {
                entered.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
            }
            Files.write(target, byteArrayOf(1))
        }
        catalog(downloader).use { catalog ->
            try {
                catalog.install("piper-ru-irina")
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                catalog.install("piper-ru-ruslan")
                catalog.cancel("piper-ru-irina")
                release.countDown()
                awaitSpeech { catalog.list().first { it.id == "piper-ru-ruslan" }.installed }

                assertEquals(listOf("sherpa-runtime", "piper-ru-ruslan"), order.toList())
                assertFalse(catalog.list().first { it.id == "piper-ru-irina" }.installed)
                assertEquals(
                    SpeechDownloadPhase.CANCELLED,
                    catalog.state().downloads.first { it.assetId == "piper-ru-irina" }.phase,
                )
            } finally {
                release.countDown()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `cancelling the runtime stops every dependent plan whether or not it was separately requested`(
        separatelyRequested: Boolean,
    ) {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val downloader = SpeechDownloadClient { _, target, _ ->
            entered.countDown()
            try {
                assertTrue(released.await(5, TimeUnit.SECONDS))
                Files.write(target, byteArrayOf(1))
            } finally {
                stopped.countDown()
            }
        }
        catalog(downloader).use { catalog ->
            try {
                if (separatelyRequested) {
                    catalog.install("sherpa-runtime")
                }
                catalog.install("piper-ru-irina")
                catalog.install("piper-ru-ruslan")
                assertTrue(entered.await(5, TimeUnit.SECONDS))

                catalog.cancel("sherpa-runtime")

                assertEquals(3, catalog.state().downloads.size)
                assertTrue(catalog.state().downloads.all { it.phase == SpeechDownloadPhase.CANCELLED })
                assertTrue(stopped.await(5, TimeUnit.SECONDS))
                assertTrue(catalog.list().none { it.installed })
                awaitSpeech {
                    Files.list(directory.resolve("speech")).use { paths -> paths.findAny().isEmpty }
                }
            } finally {
                released.countDown()
            }
        }
    }

    @Test
    fun `installed legacy foreign voices stay selectable while uninstalled foreign catalog entries stay hidden`() {
        val foreign = speechDefinitions().first {
            it.asset.kind == SpeechAssetKind.SYNTHESIS && it.asset.languages == listOf("fr")
        }
        val downloader = SpeechDownloadClient { _, _, _ -> error("No network is allowed") }
        catalog(downloader).use { catalog ->
            assertTrue(catalog.list().none { it.id == foreign.asset.id })
            assertFailsWith<InvalidSpeechAssetException> { catalog.install(foreign.asset.id) }
            installSpeechFixture(directory, foreign.asset.id)
            installSpeechFixture(directory, "sherpa-runtime")

            assertTrue(catalog.list().single { it.id == foreign.asset.id }.installed)
            assertEquals(foreign, catalog.model(foreign.asset.id, SpeechAssetKind.SYNTHESIS))
            assertTrue(
                catalog.list().filter { !it.installed && it.kind == SpeechAssetKind.SYNTHESIS }.all {
                    it.languages.any { language -> language == "ru" || language == "en" }
                },
            )
        }
    }

    private fun catalog(downloader: SpeechDownloadClient): SpeechAssetCatalog = SpeechAssetCatalog(
        directory,
        Json,
        ResourceCatalog(),
        downloader,
        SpeechPreparedArchiveInstaller(),
        SpeechLicenseInstaller(Json),
        SpeechCustomModelRegistry(directory, Json),
    )
}
