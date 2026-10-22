package ru.aigram.speech

import org.springframework.stereotype.Component
import ru.aigram.domain.speech.SpeechComputeDevice
import ru.aigram.exception.impl.SpeechGpuUnavailableException
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
private const val DEVICE_RESULT = "applio-device.txt"
internal const val GPU_UNAVAILABLE_EXIT = 73
private val CONVERSION_BOOTSTRAP = """
    import os, runpy, sys, torch
    from pathlib import Path
    expected = os.environ["AIGRAM_SPEECH_COMPUTE_DEVICE"]
    if expected == "cuda:0" and not torch.cuda.is_available():
        sys.exit(73)
    from rvc.configs.config import Config
    if str(Config().device) != expected:
        sys.exit(73)
    command = runpy.run_path(sys.argv[1], run_name="aigram_applio_cli")
    command["cli"].main(args=sys.argv[2:], standalone_mode=False)
    if str(Config().device) != expected:
        sys.exit(73)
    Path(os.environ["AIGRAM_SPEECH_DEVICE_RESULT"]).write_text(expected, encoding="utf-8")
""".trimIndent()
private val COMPUTE_BOOTSTRAP = """
    import json, sys, torch
    from pathlib import Path
    available = torch.cuda.is_available()
    device = torch.cuda.get_device_properties(0) if available else None
    Path(sys.argv[1]).write_text(json.dumps({
        "cudaAvailable": available,
        "gpuName": device.name if device else "",
        "gpuMemoryBytes": device.total_memory if device else 0,
        "torchVersion": torch.__version__,
        "cudaVersion": torch.version.cuda or ""
    }), encoding="utf-8")
""".trimIndent()

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
            python.toString(), "-s", "-B", "-", script.toString(), "infer",
            "--input-path", input.toString(), "--output-path", output.toString(),
            "--pth-path", checkpoint.toString(), "--index-path", index.toString(),
            "--f0-method", "rmvpe", "--index-rate", "0.5", "--protect", "0.33",
            "--export-format", "WAV", "--embedder-model", "contentvec",
        ).directory(runtime.toFile())
        return environment(builder, runtime, working, request.conversionDevice)
    }

    internal fun computeBuilder(runtime: Path, working: Path, result: Path): ProcessBuilder {
        val root = runtime.toRealPath()
        val python = owned(root, "env/python.exe")
        val builder = ProcessBuilder(
            python.toString(),
            "-s",
            "-B",
            "-",
            result.toAbsolutePath().toString(),
        ).directory(root.toFile())
        return environment(builder, root, working, SpeechComputeDevice.CUDA)
    }

    internal fun conversionInput(): ByteArray = CONVERSION_BOOTSTRAP.toByteArray(Charsets.UTF_8)

    internal fun computeInput(): ByteArray = COMPUTE_BOOTSTRAP.toByteArray(Charsets.UTF_8)

    private fun environment(
        builder: ProcessBuilder,
        runtime: Path,
        working: Path,
        device: SpeechComputeDevice,
    ): ProcessBuilder {
        val environment = builder.environment()
        val pythonHome = runtime.resolve("env").toString()
        environment["PYTHONHOME"] = pythonHome
        environment["PYTHONPATH"] = runtime.toString()
        environment["PYTHONNOUSERSITE"] = "1"
        environment["PYTHONUTF8"] = "1"
        environment["PYTHONDONTWRITEBYTECODE"] = "1"
        environment["HF_HUB_OFFLINE"] = "1"
        environment["TRANSFORMERS_OFFLINE"] = "1"
        environment["CUDA_VISIBLE_DEVICES"] = if (device == SpeechComputeDevice.CUDA) {
            "0"
        } else {
            "-1"
        }
        environment["AIGRAM_SPEECH_COMPUTE_DEVICE"] = deviceName(device)
        environment["AIGRAM_SPEECH_DEVICE_RESULT"] = working.resolve(DEVICE_RESULT).toAbsolutePath().toString()
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

    internal fun verifiedDevice(device: SpeechComputeDevice, working: Path): String {
        val result = working.resolve(DEVICE_RESULT)
        val expected = deviceName(device)
        if (!Files.isRegularFile(result, LinkOption.NOFOLLOW_LINKS) || Files.size(result) !in 1..16 ||
            Files.readString(result) != expected
        ) {
            if (device == SpeechComputeDevice.CUDA) {
                throw SpeechGpuUnavailableException()
            }
            throw SpeechInferenceFailedException()
        }
        return expected
    }

    private fun deviceName(device: SpeechComputeDevice): String = if (device == SpeechComputeDevice.CUDA) {
        "cuda:0"
    } else {
        "cpu"
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
