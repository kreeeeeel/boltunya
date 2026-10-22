package ru.aigram.speech

import org.springframework.stereotype.Component
import ru.aigram.exception.impl.SpeechInstallFailedException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.CancellationException
import java.util.stream.Stream

private const val DOWNLOAD_BUFFER_BYTES = 65536
private const val CONNECT_TIMEOUT_MILLIS = 15000
private const val READ_TIMEOUT_MILLIS = 30000
private const val MAX_REDIRECTS = 5
private val DOWNLOAD_HOSTS = setOf(
    "github.com",
    "release-assets.githubusercontent.com",
    "objects.githubusercontent.com",
    "huggingface.co",
    "cas-bridge.xethub.hf.co",
    "us.aws.cdn.hf.co",
    "cdn-lfs.huggingface.co",
    "cdn-lfs-us-1.hf.co",
)

@Component
class SpeechArtifactDownloader : SpeechDownloadClient {
    override fun download(definition: SpeechAssetDefinition, target: Path, progress: (Long) -> Unit) {
        val connection = open(URI(definition.downloadUrl), 0)
        try {
            val size = connection.contentLengthLong
            if (size > 0 && size != definition.asset.sizeBytes) {
                throw SpeechInstallFailedException()
            }
            connection.inputStream.use { input ->
                copy(definition, input, target, progress)
            }
        } finally {
            connection.disconnect()
        }
    }

    internal fun copy(definition: SpeechAssetDefinition, input: InputStream, target: Path, progress: (Long) -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256")
        var downloaded = 0L
        Files.newOutputStream(target).use { output ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
            Stream.generate { input.read(buffer) }.takeWhile { it >= 0 }.forEach { count ->
                if (Thread.currentThread().isInterrupted) {
                    throw CancellationException()
                }
                downloaded += count
                if (downloaded > definition.asset.sizeBytes) {
                    throw SpeechInstallFailedException()
                }
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                progress(downloaded)
            }
        }
        if (downloaded != definition.asset.sizeBytes ||
            HexFormat.of().formatHex(digest.digest()) != definition.sha256
        ) {
            throw SpeechInstallFailedException()
        }
    }

    private fun open(uri: URI, redirects: Int): HttpURLConnection {
        if (uri.scheme != "https" || uri.host !in DOWNLOAD_HOSTS || uri.userInfo != null || redirects > MAX_REDIRECTS) {
            throw SpeechInstallFailedException()
        }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.instanceFollowRedirects = false
        val status = connection.responseCode
        if (status == HttpURLConnection.HTTP_OK) {
            return connection
        }
        val location = connection.getHeaderField("Location")
        connection.disconnect()
        if (status !in setOf(301, 302, 303, 307, 308) || location.isNullOrBlank()) {
            throw SpeechInstallFailedException()
        }
        return open(uri.resolve(location), redirects + 1)
    }
}
