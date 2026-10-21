package ru.aigram.speech

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.WaveReader
import ru.aigram.exception.impl.InvalidSpeechRequestException
import ru.aigram.exception.impl.SpeechInferenceFailedException
import java.nio.file.Path

private const val INFERENCE_THREADS = 2

/** Runs only in the owned worker JVM; native output is discarded by its parent. */
class SherpaSpeechWorker {
    fun execute(request: SpeechWorkerRequest): SpeechWorkerResult = when (request.operation) {
        "recognition" -> recognize(request)
        "synthesis" -> synthesize(request)
        else -> throw InvalidSpeechRequestException()
    }

    private fun recognize(request: SpeechWorkerRequest): SpeechWorkerResult {
        val directory = Path.of(request.modelDirectory)
        val language = if (request.language == "auto") {
            ""
        } else {
            request.language
        }
        val whisper = OfflineWhisperModelConfig.builder()
            .setEncoder(directory.resolve("tiny-encoder.int8.onnx").toString())
            .setDecoder(directory.resolve("tiny-decoder.int8.onnx").toString())
            .setLanguage(language)
            .setTask("transcribe")
            .build()
        val model = OfflineModelConfig.builder()
            .setWhisper(whisper)
            .setTokens(directory.resolve("tiny-tokens.txt").toString())
            .setProvider("cpu")
            .setNumThreads(INFERENCE_THREADS)
            .setDebug(false)
            .build()
        val config = OfflineRecognizerConfig.builder().setOfflineModelConfig(model).build()
        val recognizer = OfflineRecognizer(config)
        try {
            val stream = recognizer.createStream()
            try {
                val wave = WaveReader(request.input)
                stream.acceptWaveform(wave.samples, wave.sampleRate)
                recognizer.decode(stream)
                return SpeechWorkerResult(text = recognizer.getResult(stream).text)
            } finally {
                stream.release()
            }
        } finally {
            recognizer.release()
        }
    }

    private fun synthesize(request: SpeechWorkerRequest): SpeechWorkerResult {
        val directory = Path.of(request.modelDirectory)
        val vits = OfflineTtsVitsModelConfig.builder()
            .setModel(directory.resolve(request.modelFile).toString())
            .setTokens(directory.resolve("tokens.txt").toString())
            .setDataDir(directory.resolve("espeak-ng-data").toString())
            .build()
        val model = OfflineTtsModelConfig.builder().setVits(vits).setProvider("cpu")
            .setNumThreads(INFERENCE_THREADS).setDebug(false).build()
        val engine = OfflineTts(OfflineTtsConfig.builder().setModel(model).build())
        try {
            val audio = engine.generate(request.text, 0, request.speed)
            if (audio.sampleRate <= 0 || audio.samples.isEmpty() || !audio.save(request.output)) {
                throw SpeechInferenceFailedException()
            }
            return SpeechWorkerResult(sampleRate = audio.sampleRate, samples = audio.samples.size.toLong())
        } finally {
            engine.release()
        }
    }
}
