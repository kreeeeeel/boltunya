package ru.aigram.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import ru.aigram.exception.IntegrationException
import ru.aigram.exception.InvalidInputException
import ru.aigram.exception.impl.InvalidModelDigestException
import ru.aigram.exception.impl.InvalidModelManifestException
import ru.aigram.exception.impl.InvalidModelSizeException
import ru.aigram.exception.impl.ModelRegistryUnavailableException
import ru.aigram.exception.impl.UnknownCatalogModelException
import ru.aigram.validation.InputRule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

class RegistryModelInstaller(
    private val root: Path = Path.of(
        System.getenv("OLLAMA_MODELS") ?: "${System.getProperty("user.home")}/.ollama/models",
    ),
    private val registry: String = "https://registry.ollama.ai",
    private val models: ModelCatalog = ModelCatalog(),
    private val http: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
        .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(15)).build(),
    private val downloader: RegistryBlobDownloader = RegistryBlobDownloader(http),
) {

    fun install(model: String, onProgress: (Int) -> Unit) {
        if (!models.contains(model)) {
            throw UnknownCatalogModelException()
        }
        val tag = model.substringAfter(':')
        val family = model.substringBefore(':')
        val manifestRequest = HttpRequest.newBuilder(URI.create("$registry/v2/library/$family/manifests/$tag"))
            .timeout(Duration.ofSeconds(30)).build()
        val manifestResponse = http.send(manifestRequest, HttpResponse.BodyHandlers.ofString())
        if (manifestResponse.statusCode() != 200) {
            throw ModelRegistryUnavailableException()
        }
        val manifest = Json.parseToJsonElement(manifestResponse.body()).jsonObject
        val config = manifest["config"]
        val layerValues = manifest["layers"]
        if (config == null || layerValues == null) {
            throw InvalidModelManifestException()
        }
        val layers = listOf(config) + layerValues.jsonArray
        val total = layers.stream().mapToLong { it.jsonObject["size"]?.jsonPrimitive?.longOrNull ?: 0 }.sum()
        var completed = 0L
        val blobRoot = root.resolve("blobs")
        Files.createDirectories(blobRoot)
        layers.stream().forEach { layer ->
            val digest = layer.jsonObject["digest"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (!InputRule.MODEL_DIGEST.accepts(digest)) {
                throw InvalidModelDigestException()
            }
            val target = blobRoot.resolve(digest.replace(':', '-'))
            if (!Files.exists(target)) {
                val size = layer.jsonObject["size"]?.jsonPrimitive?.longOrNull
                    ?: throw InvalidModelSizeException()
                downloader.download(
                    URI.create("$registry/v2/library/$family/blobs/$digest"),
                    target,
                    digest,
                    size,
                ) { count ->
                    completed += count
                    if (total > 0) onProgress((completed * 100 / total).toInt())
                }
            } else {
                completed += Files.size(target)
            }
        }
        val manifestRoot = root.resolve("manifests/registry.ollama.ai/library/$family")
        Files.createDirectories(manifestRoot)
        val temporary = manifestRoot.resolve("$tag.partial")
        Files.writeString(temporary, manifestResponse.body())
        Files.move(
            temporary,
            manifestRoot.resolve(tag),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
        onProgress(100)
    }
}
