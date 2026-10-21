package ru.aigram.speech

import kotlinx.serialization.json.Json
import ru.aigram.exception.impl.InvalidSpeechAssetException
import ru.aigram.resources.ResourceCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.test.assertTrue

fun speechDefinitions(): List<SpeechAssetDefinition> = Json.decodeFromString(
    ResourceCatalog().text("speech/catalog.json"),
)

fun speechCatalog(
    directory: Path,
    downloader: SpeechDownloadClient,
    registry: SpeechCustomModelRegistry = SpeechCustomModelRegistry(directory, Json),
): SpeechAssetCatalog = SpeechAssetCatalog(
    directory,
    Json,
    ResourceCatalog(),
    downloader,
    SpeechArchiveInstaller(),
    SpeechLicenseInstaller(Json),
    registry,
)

fun installSpeechFixture(directory: Path, id: String) {
    val definition = speechDefinitions().stream().filter { it.asset.id == id }.findFirst().orElse(null)
    if (definition == null) {
        throw InvalidSpeechAssetException()
    }
    val target = directory.resolve("speech").resolve(id)
    Files.createDirectories(target)
    definition.requiredFiles.stream().forEach {
        if (it == "espeak-ng-data") {
            Files.createDirectories(target.resolve(it))
        } else {
            Files.write(target.resolve(it), byteArrayOf(1))
        }
    }
    Files.writeString(target.resolve("artifact.sha256"), definition.sha256)
}

fun speechDigest(bytes: ByteArray): String = HexFormat.of()
    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

fun awaitSpeech(timeoutSeconds: Long = 5, condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
    Stream.generate {
        if (!condition()) {
            Thread.sleep(10)
        }
        condition()
    }.takeWhile { !it && System.nanoTime() < deadline }.forEach { }
    assertTrue(condition())
}
