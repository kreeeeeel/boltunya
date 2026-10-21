package ru.aigram.speech

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import java.io.ByteArrayOutputStream

fun speechImportFiles(): Map<String, ByteArray> = linkedMapOf(
    "voice/model.onnx" to byteArrayOf(1, 2, 3),
    "voice/tokens.txt" to "a 0".toByteArray(),
    "voice/espeak-ng-data/phondata" to byteArrayOf(1),
    "voice/espeak-ng-data/phonindex" to byteArrayOf(1),
    "voice/espeak-ng-data/phontab" to byteArrayOf(1),
    "voice/MODEL_CARD" to "Original voice conditions".toByteArray(),
    "voice/legal/LICENSE.txt" to "Original licence bytes\n".toByteArray(),
)

fun speechImportArchive(files: Map<String, ByteArray> = speechImportFiles()): ByteArray {
    val output = ByteArrayOutputStream()
    TarArchiveOutputStream(BZip2CompressorOutputStream(output)).use { archive ->
        files.entries.stream().forEach { (name, bytes) ->
            val entry = TarArchiveEntry(name)
            entry.size = bytes.size.toLong()
            archive.putArchiveEntry(entry)
            archive.write(bytes)
            archive.closeArchiveEntry()
        }
    }
    return output.toByteArray()
}
