package ru.aigram.speech.reply

import org.springframework.stereotype.Component
import ru.aigram.domain.speech.GeneratedVoice
import ru.aigram.domain.speech.OutgoingVoiceNote
import ru.aigram.domain.speech.StoredVoiceDraft
import ru.aigram.exception.impl.SpeechDraftConsumedException
import ru.aigram.exception.impl.SpeechDraftUnavailableException
import ru.aigram.telegram.TelegramGateway
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val MAX_VOICE_DRAFTS = 128
private val VOICE_RETENTION = Duration.ofHours(1)
private val ORPHAN_SWEEP_INTERVAL = Duration.ofMinutes(1)
private const val MAX_ORPHAN_SCAN = 1024L

@Component
class VoiceDraftStore(directory: Path, private val telegram: TelegramGateway, private val clock: Clock) {
    private val directory = directory.resolve("speech/generated").toAbsolutePath().normalize()
    private val entries = linkedMapOf<String, StoredVoiceDraft>()
    private var lastOrphanSweep = Instant.MIN

    @Synchronized
    fun output(): Path {
        prune()
        Files.createDirectories(directory)
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw SpeechDraftUnavailableException()
        }
        return directory.resolve(UUID.randomUUID().toString() + ".ogg")
    }

    @Synchronized
    fun register(
        ownerId: String,
        chatId: String,
        note: OutgoingVoiceNote,
        previewOnly: Boolean = false,
    ): GeneratedVoice {
        prune()
        val path = note.path.toAbsolutePath().normalize()
        val id = ownedId(path)
        if (ownerId.isEmpty() && !previewOnly || telegram.status().userId != ownerId ||
            !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || path.parent != directory ||
            !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || entries.size >= MAX_VOICE_DRAFTS ||
            id == null || Files.exists(marker(id), LinkOption.NOFOLLOW_LINKS)
        ) {
            if (id != null && !Files.exists(marker(id), LinkOption.NOFOLLOW_LINKS) &&
                Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) &&
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && entries[id]?.sending != true
            ) {
                Files.deleteIfExists(path)
            }
            throw SpeechDraftUnavailableException()
        }
        entries[id] = StoredVoiceDraft(
            ownerId,
            chatId,
            note.copy(ownerId = ownerId),
            clock.instant(),
            previewOnly = previewOnly,
        )
        return GeneratedVoice(
            id,
            "/api/speech/audio/$id",
            "audio/ogg",
            note.duration,
            note.waveform.map { it.toInt() and 0xff },
        )
    }

    @Synchronized
    fun audio(id: String): Path = checked(id).note.path

    fun send(
        chatId: String,
        id: String,
        replyToId: String,
        topicId: Int,
        automated: Boolean,
        beforeDispatch: () -> Unit = {},
    ): String {
        val draft = synchronized(this) {
            val found = checked(id)
            if (found.previewOnly || found.chatId != chatId) {
                throw SpeechDraftUnavailableException()
            }
            if (found.sentId.isNotEmpty()) {
                return found.sentId
            }
            if (found.sending || Files.exists(marker(id), LinkOption.NOFOLLOW_LINKS)) {
                throw SpeechDraftConsumedException()
            }
            found.sending = true
            found
        }

        try {
            beforeDispatch()
            if (telegram.status().userId != draft.ownerId || Thread.currentThread().isInterrupted) {
                throw SpeechDraftUnavailableException()
            }
            Files.createFile(marker(id))
        } catch (exception: Exception) {
            synchronized(this) {
                draft.sending = false
            }
            throw exception
        }
        // Keep the file after an ambiguous native timeout; TDLib may still be uploading it.
        val sentId = telegram.sendVoice(chatId, draft.note, replyToId, topicId, automated)
        synchronized(this) {
            draft.sentId = sentId
            draft.sending = false
            Files.deleteIfExists(marker(id))
        }
        return sentId
    }

    private fun checked(id: String): StoredVoiceDraft {
        prune()
        val draft = entries[id]
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) ||
            draft == null || draft.ownerId != telegram.status().userId ||
            !Files.isRegularFile(draft.note.path, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw SpeechDraftUnavailableException()
        }
        return draft
    }

    private fun prune() {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return
        }
        val cutoff = clock.instant().minus(VOICE_RETENTION)
        entries.entries.removeIf { (id, draft) ->
            if (!draft.sending && draft.createdAt.isBefore(cutoff) &&
                (!Files.exists(marker(id), LinkOption.NOFOLLOW_LINKS) || draft.sentId.isNotEmpty())
            ) {
                Files.deleteIfExists(draft.note.path)
                Files.deleteIfExists(marker(id))
                true
            } else {
                false
            }
        }
        pruneOrphans(cutoff)
    }

    private fun pruneOrphans(cutoff: Instant) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) ||
            clock.instant().isBefore(lastOrphanSweep.plus(ORPHAN_SWEEP_INTERVAL))
        ) {
            return
        }
        lastOrphanSweep = clock.instant()
        Files.list(directory).use { files ->
            files.limit(MAX_ORPHAN_SCAN).forEach { path ->
                val id = ownedId(path)
                if (id != null && !entries.containsKey(id) &&
                    !Files.exists(marker(id), LinkOption.NOFOLLOW_LINKS)
                ) {
                    val attributes = try {
                        Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                    } catch (exception: NoSuchFileException) {
                        return@forEach
                    }
                    if (attributes.isRegularFile && attributes.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(path)
                    }
                }
            }
        }
    }

    private fun marker(id: String): Path = directory.resolve("$id.sending")

    private fun ownedId(path: Path): String? {
        val name = path.fileName.toString()
        if (path.parent != directory || !name.endsWith(".ogg")) {
            return null
        }
        val id = name.removeSuffix(".ogg")
        return try {
            if (UUID.fromString(id).toString() == id) {
                id
            } else {
                null
            }
        } catch (exception: IllegalArgumentException) {
            null
        }
    }
}
