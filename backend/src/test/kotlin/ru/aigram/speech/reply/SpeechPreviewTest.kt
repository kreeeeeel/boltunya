package ru.aigram.speech.reply

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.component.speech.SpeechPreviewComponent
import ru.aigram.exception.impl.SpeechDraftUnavailableException
import ru.aigram.exception.impl.SpeechInferenceFailedException
import ru.aigram.resources.ResourceCatalog
import ru.aigram.speech.SpeechDownloadClient
import ru.aigram.speech.SpeechEngine
import ru.aigram.speech.SpeechProcessExecutor
import ru.aigram.speech.SpeechWorkerRequest
import ru.aigram.speech.SpeechWorkerResult
import ru.aigram.speech.awaitSpeech
import ru.aigram.speech.codec.AudioCodec
import ru.aigram.speech.codec.SyntheticSpeechWave
import ru.aigram.speech.installSpeechFixture
import ru.aigram.speech.speechCatalog
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@EnabledOnOs(OS.WINDOWS)
class SpeechPreviewTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `settings sample uses unsaved model and rate through the queue without sending or changing preferences`() {
        val seen = AtomicReference<SpeechWorkerRequest>()
        val process = SpeechProcessExecutor { _, request, _ ->
            seen.set(request)
            SyntheticSpeechWave().write(Path.of(request.output))
            SpeechWorkerResult(sampleRate = 22050, samples = 27562)
        }
        withPreview(process) { preview, queue, drafts, gateway, settings ->
            val before = settings.get()
            val job = preview.enqueue("Exact preview text.", "piper-en-amy", 1.5)
            awaitSpeech { queue.draft(job.id).status == "ready" }
            val result = queue.draft(job.id).result
            assertNotNull(result)
            assertNotNull(result.voice)
            assertEquals("Exact preview text.", seen.get().text)
            assertTrue(seen.get().modelDirectory.endsWith("piper-en-amy"))
            assertEquals(1.5f, seen.get().speed)
            assertEquals(before, settings.get())
            assertEquals("", queue.activities().single().chatId)
            assertTrue(drafts.audio(result.voice.id).fileName.toString().endsWith(".ogg"))
            assertFailsWith<SpeechDraftUnavailableException> {
                drafts.send("42", result.voice.id, "", 0, false)
            }
            assertTrue(gateway.delegate.sentVoices.isEmpty())
            assertTrue(gateway.delegate.sent.isEmpty())
        }
    }

    @Test
    fun `local settings samples work before Telegram login but cannot become outgoing messages`() {
        val process = SpeechProcessExecutor { _, request, _ ->
            SyntheticSpeechWave().write(Path.of(request.output))
            SpeechWorkerResult(sampleRate = 22050, samples = 27562)
        }
        withPreview(process) { preview, queue, drafts, gateway, _ ->
            gateway.owner = ""
            val job = preview.enqueue("Local test before signing in.", "piper-en-amy", 1.0)
            awaitSpeech { queue.draft(job.id).status == "ready" }
            val voice = queue.draft(job.id).result?.voice
            assertNotNull(voice)
            assertTrue(java.nio.file.Files.isRegularFile(drafts.audio(voice.id)))
            assertFailsWith<SpeechDraftUnavailableException> { drafts.send("", voice.id, "", 0, false) }
            gateway.owner = "100"
            assertFailsWith<SpeechDraftUnavailableException> { drafts.audio(voice.id) }
        }
    }

    @Test
    fun `settings sample cancellation interrupts synthesis and never leaves a playable completed draft`() {
        val entered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val process = SpeechProcessExecutor { _, _, _ ->
            entered.countDown()
            try {
                CountDownLatch(1).await(20, TimeUnit.SECONDS)
                throw SpeechInferenceFailedException()
            } finally {
                stopped.countDown()
            }
        }
        withPreview(process) { preview, queue, _, gateway, _ ->
            val job = preview.enqueue("Cancel this sample.", "piper-en-amy", 1.0)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            queue.cancel(job.id)
            assertTrue(stopped.await(5, TimeUnit.SECONDS))
            awaitSpeech { queue.draft(job.id).status == "cancelled" }
            assertEquals(null, queue.draft(job.id).result)
            assertTrue(gateway.delegate.sentVoices.isEmpty())
        }
    }

    private fun withPreview(
        process: SpeechProcessExecutor,
        action: (SpeechPreviewComponent, ReplyCoordinator, VoiceDraftStore, VoiceTestGateway, SettingsStore) -> Unit,
    ) {
        listOf("sherpa-runtime", "piper-en-amy").stream().forEach { installSpeechFixture(directory, it) }
        val settings = SettingsStore(directory)
        val gateway = VoiceTestGateway()
        val tracing = ReplyTraceScope()
        val drafts = VoiceDraftStore(directory, gateway, VoiceTestClock())
        val downloader = SpeechDownloadClient { _, _, _ -> throw SpeechInferenceFailedException() }
        speechCatalog(directory, downloader).use { catalog ->
            val service = VoiceReplyService(
                SpeechEngine(directory, catalog, process),
                catalog,
                AudioCodec(directory),
                drafts,
                settings,
                gateway,
                tracing,
            )
            ReplyCoordinator(settings, gateway, tracing = tracing) { _, _ ->
                throw SpeechInferenceFailedException()
            }.use { queue ->
                action(
                    SpeechPreviewComponent(queue, gateway, catalog, service, ResourceCatalog()),
                    queue,
                    drafts,
                    gateway,
                    settings,
                )
            }
        }
    }
}
