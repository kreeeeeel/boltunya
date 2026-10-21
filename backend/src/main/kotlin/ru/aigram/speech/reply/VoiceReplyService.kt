package ru.aigram.speech.reply

import org.springframework.stereotype.Component
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.events.ActivityPhase
import ru.aigram.domain.settings.AppSettings
import ru.aigram.domain.speech.SpeechAssetKind
import ru.aigram.exception.impl.InvalidSpeechRequestException
import ru.aigram.exception.impl.ReplyCancelledException
import ru.aigram.exception.impl.SpeechNotInstalledException
import ru.aigram.speech.SpeechAssetCatalog
import ru.aigram.speech.SpeechEngine
import ru.aigram.speech.codec.AudioCodec
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway
import java.nio.file.Files

private const val MAX_SPOKEN_CHARACTERS = 4096

@Component
class VoiceReplyService(
    private val engine: SpeechEngine,
    private val catalog: SpeechAssetCatalog,
    private val codec: AudioCodec,
    private val drafts: VoiceDraftStore,
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val tracing: ReplyTraceScope,
) {
    fun ensureReady(preferences: AppSettings) {
        val assets = catalog.list()
        val model = assets.stream().filter {
            it.id == preferences.voiceSynthesisModel && it.kind == SpeechAssetKind.SYNTHESIS
        }.findFirst().orElse(null)
        if (model == null || !model.installed || model.dependencies.stream().anyMatch { id ->
                assets.stream().noneMatch { it.id == id && it.installed }
            }
        ) {
            throw SpeechNotInstalledException()
        }
    }

    fun compose(result: ReplyResult, chatId: String, ownerId: String, preferences: AppSettings): ReplyResult {
        ensureReady(preferences)
        return synthesize(
            result,
            chatId,
            ownerId,
            preferences.voiceSynthesisModel,
            preferences.voiceSpeechRate,
            false,
        ) {
            settings.get().voiceSynthesisModel == preferences.voiceSynthesisModel &&
                settings.get().voiceSpeechRate == preferences.voiceSpeechRate
        }
    }

    fun sample(text: String, modelId: String, speed: Double, ownerId: String): ReplyResult =
        synthesize(ReplyResult(text, 0, 0.0, modelId), "", ownerId, modelId, speed, true) { true }

    private fun synthesize(
        result: ReplyResult,
        chatId: String,
        ownerId: String,
        modelId: String,
        speed: Double,
        previewOnly: Boolean,
        current: () -> Boolean,
    ): ReplyResult {
        if (result.text.isBlank() || result.text.length > MAX_SPOKEN_CHARACTERS) {
            throw InvalidSpeechRequestException()
        }
        tracing.phase(ActivityPhase.SYNTHESIZING)
        val output = drafts.output()
        val started = System.nanoTime()
        val wave = engine.synthesize(result.text, modelId, speed)
        try {
            val note = codec.toVoiceNote(wave.path, output)
            if (Thread.currentThread().isInterrupted || telegram.status().userId != ownerId ||
                !current()
            ) {
                throw ReplyCancelledException()
            }
            return result.copy(
                voice = drafts.register(ownerId, chatId, note, previewOnly),
                stickerId = "",
                entities = emptyList(),
                durationMs =
                result.durationMs + java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
            )
        } catch (exception: Exception) {
            Files.deleteIfExists(output)
            throw exception
        } finally {
            Files.deleteIfExists(wave.path)
        }
    }
}
