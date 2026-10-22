package ru.aigram.speech

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.speech.SpeechComputeDevice
import ru.aigram.exception.impl.SpeechGpuUnavailableException
import ru.aigram.exception.impl.SpeechInferenceFailedException
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplioSpeechWorkerTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `pinned portable CLI runs CPU inference without installer webserver network or transcript arguments`() {
        val request = request()
        val worker = ApplioSpeechWorker().builder(request, directory)
        val command = worker.command()

        assertEquals(Path.of(request.runtimeDirectory).resolve("env/python.exe").toRealPath().toString(), command[0])
        assertEquals("-s", command[1])
        assertEquals("-B", command[2])
        assertEquals("-", command[3])
        assertEquals(Path.of(request.runtimeDirectory).resolve("core.py").toRealPath().toString(), command[4])
        assertEquals("infer", command[5])
        assertTrue(command.containsAll(listOf("--pth-path", "--index-path", "--f0-method", "rmvpe")))
        assertFalse(command.any { it.contains("private synthetic transcript") || it.endsWith(".bat") })
        assertEquals("-1", worker.environment()["CUDA_VISIBLE_DEVICES"])
        assertEquals("1", worker.environment()["HF_HUB_OFFLINE"])
        assertEquals("1", worker.environment()["TRANSFORMERS_OFFLINE"])
        assertEquals("2", worker.environment()["OMP_NUM_THREADS"])
        assertEquals(
            directory.resolve("applio-config/config.json").toString(),
            worker.environment()["APPLIO_CONFIG_FILE"],
        )
        assertEquals(directory.resolve("applio-logs").toString(), worker.environment()["APPLIO_LOGS_DIR"])
        assertEquals(Path.of(request.runtimeDirectory).toRealPath().toString(), worker.environment()["APPLIO_ROOT"])
        assertEquals(
            Path.of(request.runtimeDirectory).toRealPath().toString(),
            worker.environment()["APPLIO_CODE_ROOT"],
        )
        assertEquals(directory.resolve("torch").toString(), worker.environment()["TORCH_HOME"])
        assertEquals(ProcessBuilder.Redirect.DISCARD, worker.redirectError())
        assertEquals(ProcessBuilder.Redirect.DISCARD, worker.redirectOutput())
    }

    @Test
    fun `explicit CUDA selects device zero and requires actual Applio config before and after inference`() {
        val worker = ApplioSpeechWorker().builder(
            request().copy(conversionDevice = SpeechComputeDevice.CUDA),
            directory,
        )
        val bootstrap = ApplioSpeechWorker().conversionInput().toString(Charsets.UTF_8)

        assertEquals("0", worker.environment()["CUDA_VISIBLE_DEVICES"])
        assertEquals("cuda:0", worker.environment()["AIGRAM_SPEECH_COMPUTE_DEVICE"])
        assertTrue(bootstrap.contains("not torch.cuda.is_available()"))
        assertTrue(bootstrap.indexOf("str(Config().device) != expected") < bootstrap.indexOf("command[\"cli\"].main"))
        assertTrue(
            bootstrap.lastIndexOf("str(Config().device) != expected") > bootstrap.indexOf("command[\"cli\"].main"),
        )
        assertFalse(worker.command().contains("--device"))
        assertFalse(worker.command().contains("-c"))
        assertFalse(worker.command().contains(bootstrap))
        assertFalse(bootstrap.contains(".to(\"cpu\")"))
    }

    @Test
    fun `successful process requires an exact bounded device receipt and never accepts CPU as CUDA`() {
        val worker = ApplioSpeechWorker()
        assertFailsWith<SpeechGpuUnavailableException> { worker.verifiedDevice(SpeechComputeDevice.CUDA, directory) }
        val receipt = Files.writeString(directory.resolve("applio-device.txt"), "cpu")
        assertEquals("cpu", worker.verifiedDevice(SpeechComputeDevice.CPU, directory))
        assertFailsWith<SpeechGpuUnavailableException> { worker.verifiedDevice(SpeechComputeDevice.CUDA, directory) }
        Files.writeString(receipt, "cuda:0")
        assertEquals("cuda:0", worker.verifiedDevice(SpeechComputeDevice.CUDA, directory))
        assertFailsWith<SpeechInferenceFailedException> { worker.verifiedDevice(SpeechComputeDevice.CPU, directory) }
        Files.writeString(receipt, "cuda:0".repeat(100))
        assertFailsWith<SpeechGpuUnavailableException> { worker.verifiedDevice(SpeechComputeDevice.CUDA, directory) }
    }

    @Test
    fun `capability command only reads torch metadata without tensors weights or inference`() {
        val request = request()
        val builder = ApplioSpeechWorker().computeBuilder(
            Path.of(request.runtimeDirectory),
            directory,
            directory.resolve("status.json"),
        )
        val code = ApplioSpeechWorker().computeInput().toString(Charsets.UTF_8)

        assertTrue(code.contains("torch.cuda.is_available()"))
        assertTrue(code.contains("torch.cuda.get_device_properties(0)"))
        assertFalse(code.contains("torch.load"))
        assertFalse(code.contains("runpy"))
        assertFalse(code.contains("Config"))
        assertFalse(code.contains("torch.tensor"))
        assertEquals("-", builder.command()[3])
        assertEquals(directory.resolve("status.json").toAbsolutePath().toString(), builder.command()[4])
        assertFalse(builder.command().contains("-c"))
        assertFalse(builder.command().contains(code))
        assertEquals("0", builder.environment()["CUDA_VISIBLE_DEVICES"])
        assertEquals(directory.toString(), builder.environment()["TEMP"])
        assertEquals(ProcessBuilder.Redirect.DISCARD, builder.redirectOutput())
    }

    @Test
    fun `model traversal missing index and identical input output are rejected before spawning`() {
        val request = request()
        val worker = ApplioSpeechWorker()

        assertFailsWith<SpeechInferenceFailedException> {
            worker.builder(request.copy(modelFile = "../outside.pth"), directory)
        }
        assertFailsWith<SpeechInferenceFailedException> {
            worker.builder(request.copy(indexFile = "missing.index"), directory)
        }
        assertFailsWith<SpeechInferenceFailedException> {
            worker.builder(request.copy(output = request.input), directory)
        }
    }

    @Test
    fun `converted output must be bounded PCM mono audio instead of an arbitrary successful process file`() {
        val output = directory.resolve("output.wav")
        val format = AudioFormat(40000f, 16, 1, true, false)
        AudioInputStream(ByteArrayInputStream(ByteArray(80000)), format, 40000).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, output.toFile())
        }
        val worker = ApplioSpeechWorker()
        val result = worker.result(output)
        assertEquals(40000, result.sampleRate)
        assertEquals(40000L, result.samples)

        Files.writeString(output, "not an audio file")
        assertFailsWith<javax.sound.sampled.UnsupportedAudioFileException> { worker.result(output) }
    }

    private fun request(): SpeechWorkerRequest {
        val runtime = directory.resolve("runtime")
        val model = directory.resolve("model")
        listOf(
            "env/python.exe",
            "core.py",
            "rvc/models/embedders/contentvec/config.json",
            "rvc/models/embedders/contentvec/pytorch_model.bin",
            "rvc/models/predictors/rmvpe.pt",
        ).stream().forEach {
            val path = runtime.resolve(it)
            Files.createDirectories(path.parent)
            Files.write(path, byteArrayOf(1))
        }
        Files.createDirectories(model)
        Files.write(model.resolve("voice.pth"), byteArrayOf(1))
        Files.write(model.resolve("voice.index"), byteArrayOf(1))
        val input = Files.write(directory.resolve("input.wav"), byteArrayOf(1))
        return SpeechWorkerRequest(
            "conversion",
            model.toString(),
            "voice.pth",
            input = input.toString(),
            output = directory.resolve("output.wav").toString(),
            runtimeDirectory = runtime.toString(),
            indexFile = "voice.index",
            text = "private synthetic transcript",
        )
    }
}
