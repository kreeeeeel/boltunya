package ru.aigram.speech

import org.springframework.stereotype.Component
import ru.aigram.exception.impl.SpeechInferenceFailedException
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

private const val MAX_CONVERTED_WAVE_BYTES = 64L * 1024 * 1024
private const val MAX_CONVERTED_AUDIO_SECONDS = 600
private const val CONVERSION_THREADS = "2"

@Component
class ApplioSpeechWorker {
    fun builder(request: SpeechWorkerRequest, working: Path): ProcessBuilder {
        val runtime = Path.of(request.runtimeDirectory).toRealPath()
        val model = Path.of(request.modelDirectory).toRealPath()
        val python = owned(runtime, "env/python.exe")
        val script = owned(runtime, "core.py")
        val checkpoint = owned(model, request.modelFile)
        val index = owned(model, request.indexFile)
        owned(runtime, "rvc/models/embedders/contentvec/config.json")
        owned(runtime, "rvc/models/embedders/contentvec/pytorch_model.bin")
        owned(runtime, "rvc/models/predictors/rmvpe.pt")
        val input = Path.of(request.input).toAbsolutePath().normalize()
        val output = Path.of(request.output).toAbsolutePath().normalize()
        if (!Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS) || input == output ||
            !request.modelFile.endsWith(".pth") || !request.indexFile.endsWith(".index")
        ) {
            throw SpeechInferenceFailedException()
        }
        val builder = ProcessBuilder(
            python.toString(), "-s", script.toString(), "infer",
            "--input-path", input.toString(), "--output-path", output.toString(),
            "--pth-path", checkpoint.toString(), "--index-path", index.toString(),
            "--f0-method", "rmvpe", "--index-rate", "0.5", "--protect", "0.33",
            "--export-format", "WAV", "--embedder-model", "contentvec",
        ).directory(runtime.toFile())
        val environment = builder.environment()
        val pythonHome = runtime.resolve("env").toString()
        environment["PYTHONHOME"] = pythonHome
        environment["PYTHONPATH"] = runtime.toString()
        environment["PYTHONNOUSERSITE"] = "1"
        environment["PYTHONUTF8"] = "1"
        environment["PYTHONDONTWRITEBYTECODE"] = "1"
        environment["HF_HUB_OFFLINE"] = "1"
        environment["TRANSFORMERS_OFFLINE"] = "1"
        environment["CUDA_VISIBLE_DEVICES"] = "-1"
        environment.remove("APPLIO_EXPERIMENTAL_ONNX_INFERENCE")
        environment["OMP_NUM_THREADS"] = CONVERSION_THREADS
        environment["MKL_NUM_THREADS"] = CONVERSION_THREADS
        environment["OPENBLAS_NUM_THREADS"] = CONVERSION_THREADS
        environment["NUMBA_NUM_THREADS"] = CONVERSION_THREADS
        environment["TOKENIZERS_PARALLELISM"] = "false"
        environment["APPLIO_CONFIG_DIR"] = working.resolve("applio-config").toString()
        environment["APPLIO_CONFIG_FILE"] = working.resolve("applio-config/config.json").toString()
        environment["APPLIO_ROOT"] = runtime.toString()
        environment["APPLIO_CODE_ROOT"] = runtime.toString()
        environment["APPLIO_LOGS_DIR"] = working.resolve("applio-logs").toString()
        environment["HF_HOME"] = working.resolve("huggingface").toString()
        environment["TORCH_HOME"] = working.resolve("torch").toString()
        environment["TEMP"] = working.toString()
        environment["TMP"] = working.toString()
        val paths = listOf(
            runtime.resolve("env"),
            runtime.resolve("env/DLLs"),
            runtime.resolve("env/Library/bin"),
            runtime.resolve("env/Scripts"),
            runtime.resolve("env/Lib/site-packages/torch/lib"),
        ).stream().map(Path::toString).toList()
        environment["PATH"] = (paths + environment["PATH"].orEmpty()).joinToString(File.pathSeparator)
        return builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
    }

    fun result(output: Path): SpeechWorkerResult {
        if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(output) !in 1..MAX_CONVERTED_WAVE_BYTES
        ) {
            throw SpeechInferenceFailedException()
        }
        AudioSystem.getAudioInputStream(output.toFile()).use { wave ->
            val format = wave.format
            val rate = format.sampleRate.toInt()
            if (format.encoding != AudioFormat.Encoding.PCM_SIGNED || format.channels != 1 ||
                format.sampleSizeInBits != 16 || rate !in 8000..48000 ||
                wave.frameLength !in 1..rate.toLong() * MAX_CONVERTED_AUDIO_SECONDS
            ) {
                throw SpeechInferenceFailedException()
            }
            return SpeechWorkerResult(sampleRate = rate, samples = wave.frameLength)
        }
    }

    private fun owned(directory: Path, relative: String): Path {
        val path = directory.resolve(relative).normalize()
        if (relative.isEmpty() || !path.startsWith(directory) ||
            !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().startsWith(directory)
        ) {
            throw SpeechInferenceFailedException()
        }
        return path.toRealPath()
    }
}
