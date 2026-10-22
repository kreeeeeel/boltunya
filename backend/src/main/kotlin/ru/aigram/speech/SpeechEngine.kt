package ru.aigram.speech

import org.springframework.stereotype.Component
import ru.aigram.domain.speech.SpeechAssetKind
import ru.aigram.domain.speech.SpeechEngineKind
import ru.aigram.domain.speech.SpeechWave
import ru.aigram.exception.impl.InvalidSpeechRequestException
import ru.aigram.exception.impl.SpeechInferenceFailedException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.Semaphore

private const val MAX_INPUT_BYTES = 25L * 1024 * 1024
private const val MAX_TEXT_LENGTH = 4096
private const val MAX_OUTPUT_BYTES = 64L * 1024 * 1024
private const val MAX_AUDIO_MILLIS = 600000L
private const val RUNTIME_ID = "sherpa-runtime"
private const val CONVERSION_RUNTIME_ID = "applio-runtime"

@Component
class SpeechEngine(
    directory: Path,
    private val catalog: SpeechAssetCatalog,
    private val process: SpeechProcessExecutor,
) {
    private val workingDirectory = directory.resolve("speech").resolve("work")
    private val inference = Semaphore(1)

    fun transcribe(wav: Path, modelId: String, language: String = "auto"): String {
        val model = catalog.model(modelId, SpeechAssetKind.RECOGNITION)
        if (!Files.isRegularFile(wav) || Files.size(wav) !in 1..MAX_INPUT_BYTES ||
            (language != "auto" && language !in model.asset.languages)
        ) {
            throw InvalidSpeechRequestException()
        }
        return execute(
            SpeechWorkerRequest(
                "recognition",
                catalog.location(modelId).toString(),
                input = wav.toString(),
                language = language,
            ),
        ).text
    }

    fun synthesize(text: String, modelId: String, speed: Double = 1.0): SpeechWave {
        val model = catalog.model(modelId, SpeechAssetKind.SYNTHESIS)
        if (text.isBlank() || text.length > MAX_TEXT_LENGTH || !speed.isFinite() || speed !in 0.5..2.0) {
            throw InvalidSpeechRequestException()
        }
        Files.createDirectories(workingDirectory)
        val wave = Files.createTempFile(workingDirectory, "voice-", ".wav")
        try {
            val response = if (model.asset.engine == SpeechEngineKind.APPLIO_RVC) {
                convert(text, speed, model, wave)
            } else {
                execute(
                    SpeechWorkerRequest(
                        "synthesis",
                        catalog.location(modelId).toString(),
                        model.modelFile,
                        output = wave.toString(),
                        text = text,
                        speed = speed.toFloat(),
                    ),
                )
            }
            val duration = if (response.sampleRate > 0) {
                response.samples * 1000 / response.sampleRate
            } else {
                0
            }
            if (!Files.isRegularFile(wave) || Files.size(wave) !in 1..MAX_OUTPUT_BYTES ||
                duration !in 1..MAX_AUDIO_MILLIS
            ) {
                throw SpeechInferenceFailedException()
            }
            return SpeechWave(wave, response.sampleRate, duration)
        } catch (exception: Exception) {
            Files.deleteIfExists(wave)
            throw exception
        }
    }

    private fun convert(text: String, speed: Double, model: SpeechAssetDefinition, output: Path): SpeechWorkerResult {
        if (model.baseModelId.isEmpty() ||
            catalog.model(model.baseModelId, SpeechAssetKind.SYNTHESIS).asset.engine != SpeechEngineKind.SHERPA
        ) {
            throw InvalidSpeechRequestException()
        }
        val base = synthesize(text, model.baseModelId, speed)
        try {
            return execute(
                SpeechWorkerRequest(
                    "conversion",
                    catalog.location(model.asset.id).toString(),
                    model.modelFile,
                    input = base.path.toString(),
                    output = output.toString(),
                    runtimeDirectory = catalog.location(CONVERSION_RUNTIME_ID).toString(),
                    indexFile = model.indexFile,
                ),
            )
        } finally {
            Files.deleteIfExists(base.path)
        }
    }

    private fun execute(request: SpeechWorkerRequest): SpeechWorkerResult {
        inference.acquire()
        try {
            Files.createDirectories(workingDirectory)
            val directory = Files.createTempDirectory(workingDirectory, "request-")
            try {
                return process.execute(
                    catalog.location(RUNTIME_ID).resolve("native.jar"),
                    request,
                    directory.resolve("result.json"),
                )
            } finally {
                Files.walk(directory).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        } finally {
            inference.release()
        }
    }
}
