package ru.aigram.speech.reply

import org.springframework.stereotype.Component
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.events.ActivityPhase
import ru.aigram.domain.speech.SpeechAssetKind
import ru.aigram.domain.speech.SpeechComputeDevice
import ru.aigram.domain.speech.VoicePreferences
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
    fun ensureReady(preferences: VoicePreferences) {
        val assets = catalog.list()
        val model = assets.stream().filter {
            it.id == preferences.synthesisModel && it.kind == SpeechAssetKind.SYNTHESIS
        }.findFirst().orElse(null)
        if (model == null || !model.installed || model.dependencies.stream().anyMatch { id ->
                assets.stream().noneMatch { it.id == id && it.installed }
            }
        ) {
            throw SpeechNotInstalledException()
        }
    }

    fun compose(
        result: ReplyResult,
        chatId: String,
        ownerId: String,
        preferences: VoicePreferences,
        rule: ChatRule = settings.rule(chatId),
    ): ReplyResult {
        ensureReady(preferences)
        val storedRule = settings.rule(chatId)
        return synthesize(
            result,
            chatId,
            ownerId,
            preferences.synthesisModel,
            preferences.speechRate,
            false,
            preferences.conversionDevice,
        ) {
            settings.rule(chatId) == storedRule && settings.get().voicePreferences(rule) == preferences
        }
    }

    fun sample(
        text: String,
        modelId: String,
        speed: Double,
        ownerId: String,
        conversionDevice: SpeechComputeDevice = settings.get().voiceConversionDevice,
    ): ReplyResult = synthesize(
        ReplyResult(text, 0, 0.0, modelId),
        "",
        ownerId,
        modelId,
        speed,
        true,
        conversionDevice,
    ) { true }

    fun draft(
        text: String,
        chatId: String,
        ownerId: String,
        modelId: String,
        speed: Double,
        conversionDevice: SpeechComputeDevice = settings.get().voiceConversionDevice,
    ): ReplyResult = synthesize(
        ReplyResult(text, 0, 0.0, modelId),
        chatId,
        ownerId,
        modelId,
        speed,
        false,
        conversionDevice,
    ) { true }

    private fun synthesize(
        result: ReplyResult,
        chatId: String,
        ownerId: String,
        modelId: String,
        speed: Double,
        previewOnly: Boolean,
        conversionDevice: SpeechComputeDevice,
        current: () -> Boolean,
    ): ReplyResult {
        if (result.text.isBlank() || result.text.length > MAX_SPOKEN_CHARACTERS) {
            throw InvalidSpeechRequestException()
        }
        tracing.phase(ActivityPhase.SYNTHESIZING)
        val output = drafts.output()
        val started = System.nanoTime()
        val wave = engine.synthesize(result.text, modelId, speed, conversionDevice)
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
