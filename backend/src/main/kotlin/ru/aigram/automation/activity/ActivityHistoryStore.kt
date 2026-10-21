package ru.aigram.automation.activity

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.ActivityDetails
import ru.aigram.domain.ActivityEntry
import ru.aigram.domain.ActivityHistoryPage
import ru.aigram.domain.ActivityHistorySettings
import ru.aigram.domain.ActivityHistoryStorage
import ru.aigram.exception.impl.ActivityNotFoundException
import ru.aigram.exception.impl.MalformedPayloadException
import ru.aigram.resources.ResourceCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Clock
import java.util.Base64
import java.util.HexFormat
import java.util.NavigableSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val PAGE_LIMIT = 100
private const val RECORD_LIMIT_BYTES = 32L * 1024 * 1024
private const val MEBIBYTE = 1024L * 1024
private const val SECONDS_PER_DAY = 86400L
private val ACTIVE_STATUSES = setOf("queued", "generating")

/** Local, account-scoped conversation history. Diagnostics never receive its contents. */
@Component
class ActivityHistoryStore @Inject constructor(directory: Path, private val clock: Clock, resources: ResourceCatalog) :
    AutoCloseable {
    private val closed = AtomicBoolean()
    private val root = directory.resolve("activity")
    private val settingsFile = root.resolve("history-settings.json")
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val interrupted = resources.text("activity/ru/interrupted.txt")
    private val indexes = ConcurrentHashMap<String, NavigableSet<String>>()
    private val sizes = ConcurrentHashMap<String, Long>()
    private val errors = ConcurrentHashMap<String, String>()
    private val tracked = ConcurrentHashMap<String, TrackedActivity>()
    private val fingerprints = ConcurrentHashMap<String, String>()
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "activity-history").also { it.isDaemon = true }
    }

    @Volatile private var policy = if (Files.isRegularFile(settingsFile)) {
        json.decodeFromString<ActivityHistorySettings>(Files.readString(settingsFile))
    } else {
        ActivityHistorySettings()
    }
    private val ticker = executor.scheduleWithFixedDelay(::flush, 500, 500, TimeUnit.MILLISECONDS)

    fun watch(ownerId: String, trace: ReplyTrace) {
        val id = trace.summary().id
        val quota = policy.maxStorageMb
        val hasRoom = quota == 0 || (sizes[ownerId] ?: 0) < quota * MEBIBYTE
        if (validOwner(ownerId) && (tracked.containsKey(id) || errors[ownerId].isNullOrEmpty() && hasRoom)) {
            tracked[id] = TrackedActivity(ownerId, trace)
        }
    }

    @Synchronized
    fun flush() {
        val snapshot = tracked.entries.toList()
        val failed = mutableMapOf<String, String>()
        snapshot.stream().forEach { (id, source) ->
            try {
                index(source.ownerId)
                val details = source.trace.details()
                val content = json.encodeToString(details)
                val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
                val fingerprint = HexFormat.of().formatHex(digest)
                if (fingerprints[id] != fingerprint) {
                    if (save(source.ownerId, details, content)) {
                        fingerprints[id] = fingerprint
                        if (details.entry.status !in ACTIVE_STATUSES) {
                            tracked.remove(id, source)
                            fingerprints.remove(id)
                        }
                    } else {
                        failed[source.ownerId] = errors[source.ownerId] ?: "ACTIVITY_HISTORY_WRITE_FAILED"
                    }
                }
            } catch (exception: Exception) {
                failed[source.ownerId] = "ACTIVITY_HISTORY_WRITE_FAILED"
                System.err.println("Activity history write failed: ${exception.javaClass.simpleName}")
            }
        }
        snapshot.stream().map { it.value.ownerId }.distinct().forEach { owner ->
            val error = failed[owner]
            if (error == null) errors.remove(owner) else errors[owner] = error
        }
    }

    @Synchronized
    fun page(ownerId: String, cursor: String?, limit: Int): ActivityHistoryPage {
        if (limit !in 1..PAGE_LIMIT) throw MalformedPayloadException()
        if (!validOwner(ownerId)) return ActivityHistoryPage()
        val keys = index(ownerId)
        applyRetention(ownerId, keys)
        val after = cursor?.takeIf(String::isNotEmpty)?.let(::decodeCursor)
        val selected = keys.stream().filter { after == null || it < after }.limit(limit.toLong() + 1).toList()
        val more = selected.size > limit
        val page = selected.take(limit)
        val entries = page.stream().map { key ->
            json.decodeFromString<ActivityEntry>(Files.readString(history(ownerId).resolve("$key.entry.json")))
        }.toList()
        return ActivityHistoryPage(
            entries,
            if (more) encodeCursor(page.last()) else null,
            more,
            keys.size,
            storage(ownerId),
        )
    }

    @Synchronized
    fun details(ownerId: String, id: String): ActivityDetails {
        if (!validOwner(ownerId) || !validId(id)) throw ActivityNotFoundException()
        val key = index(ownerId).stream().filter { it.endsWith("-$id") }.findFirst().orElse(null)
            ?: throw ActivityNotFoundException()
        val file = history(ownerId).resolve("$key.trace.json")
        if (!Files.isRegularFile(file) || Files.size(file) > RECORD_LIMIT_BYTES) throw ActivityNotFoundException()
        return json.decodeFromString(Files.readString(file))
    }

    @Synchronized
    fun storage(ownerId: String): ActivityHistoryStorage {
        if (validOwner(ownerId)) index(ownerId)
        val overQuota = policy.maxStorageMb > 0 && (sizes[ownerId] ?: 0) >= policy.maxStorageMb * MEBIBYTE
        val error = errors[ownerId].orEmpty().ifEmpty { if (overQuota) "ACTIVITY_HISTORY_QUOTA_EXCEEDED" else "" }
        return ActivityHistoryStorage(
            policy.retentionDays,
            policy.maxStorageMb,
            sizes[ownerId] ?: 0,
            error.isEmpty(),
            error,
        )
    }

    @Synchronized
    fun configure(ownerId: String, settings: ActivityHistorySettings): ActivityHistoryStorage {
        Files.createDirectories(root)
        atomic(settingsFile, json.encodeToString(settings))
        policy = settings
        errors.remove(ownerId)
        if (validOwner(ownerId)) applyRetention(ownerId, index(ownerId))
        return storage(ownerId)
    }

    @Synchronized
    fun clear(ownerId: String) {
        if (!validOwner(ownerId)) return
        val keys = index(ownerId)
        keys.toList().stream().forEach { remove(ownerId, it) }
        val completed = tracked.entries.stream().filter {
            it.value.ownerId == ownerId && !it.value.trace.active()
        }.map { it.key }.toList()
        completed.stream().forEach { id ->
            val active = root.resolve(ownerId).resolve("active/$id.json")
            if (Files.isRegularFile(active)) {
                sizes.compute(ownerId) { _, size -> (size ?: 0) - Files.size(active) }
                Files.delete(active)
            }
            tracked.remove(id)
            fingerprints.remove(id)
        }
        // Remove terminal files whose summary write was interrupted before they entered the index.
        Files.list(history(ownerId)).use { files ->
            files.filter { path ->
                val name = path.fileName.toString()
                name.endsWith(".trace.json") && validKey(name.removeSuffix(".trace.json"))
            }.forEach { path ->
                val size = Files.size(path)
                Files.delete(path)
                sizes.compute(ownerId) { _, previous -> (previous ?: 0) - size }
            }
        }
        fingerprints.keys.retainAll(tracked.keys)
        errors.remove(ownerId)
    }

    private fun index(ownerId: String): NavigableSet<String> {
        val existing = indexes[ownerId]
        if (existing != null) return existing
        val directory = history(ownerId)
        Files.createDirectories(directory)
        val keys = ConcurrentSkipListSet<String>(Comparator.reverseOrder())
        Files.list(directory).use { files ->
            files.filter { it.fileName.toString().endsWith(".entry.json") }.forEach { path ->
                val key = path.fileName.toString().removeSuffix(".entry.json")
                if (validKey(key)) keys.add(key)
            }
        }
        indexes[ownerId] = keys
        sizes[ownerId] = Files.walk(root.resolve(ownerId)).use { paths ->
            paths.filter { Files.isRegularFile(it) }.mapToLong { Files.size(it) }.sum()
        }
        val active = root.resolve(ownerId).resolve("active")
        if (Files.isDirectory(active)) {
            Files.list(active).use { files ->
                files.filter { it.fileName.toString().endsWith(".json") && Files.size(it) <= RECORD_LIMIT_BYTES }
                    .forEach { path ->
                        val details = json.decodeFromString<ActivityDetails>(Files.readString(path))
                        if (tracked.containsKey(details.entry.id)) return@forEach
                        val key = details.entry.date.toString().padStart(20, '0') + "-" + details.entry.id
                        val completedFile = history(ownerId).resolve("$key.trace.json")
                        val completed = if (Files.isRegularFile(completedFile) &&
                            Files.size(completedFile) <= RECORD_LIMIT_BYTES
                        ) {
                            json.decodeFromString<ActivityDetails>(Files.readString(completedFile)).takeIf {
                                it.entry.id == details.entry.id && it.entry.status !in ACTIVE_STATUSES
                            }
                        } else {
                            null
                        }
                        val restored = if (completed != null) {
                            completed
                        } else if (details.entry.status in ACTIVE_STATUSES) {
                            val now = clock.millis()
                            details.copy(
                                entry = details.entry.copy(
                                    status = "interrupted",
                                    phase = "interrupted",
                                    detail = interrupted,
                                    finishedAt = now,
                                    durationMs = if (details.entry.startedAt > 0) now - details.entry.startedAt else 0,
                                    queuePosition = 0,
                                    thinking = "",
                                ),
                            )
                        } else {
                            details
                        }
                        save(ownerId, restored, json.encodeToString(restored), recovering = true)
                    }
            }
        }
        applyRetention(ownerId, keys)
        return keys
    }

    private fun save(ownerId: String, details: ActivityDetails, content: String, recovering: Boolean = false): Boolean {
        if (!validId(details.entry.id)) throw ActivityNotFoundException()
        if (content.toByteArray(Charsets.UTF_8).size > RECORD_LIMIT_BYTES) {
            errors[ownerId] = "ACTIVITY_HISTORY_ENTRY_TOO_LARGE"
            return false
        }
        val active = root.resolve(ownerId).resolve("active/${details.entry.id}.json")
        if (details.entry.status in ACTIVE_STATUSES) return write(ownerId, active, content, recovering)
        val key = details.entry.date.toString().padStart(20, '0') + "-" + details.entry.id
        if (!write(ownerId, history(ownerId).resolve("$key.trace.json"), content, recovering)) return false
        val summary = json.encodeToString(details.entry)
        if (!write(ownerId, history(ownerId).resolve("$key.entry.json"), summary, recovering)) return false
        indexes.getValue(ownerId).add(key)
        if (Files.isRegularFile(active)) {
            sizes.compute(ownerId) { _, size -> (size ?: 0) - Files.size(active) }
            Files.delete(active)
        }
        return true
    }

    private fun write(ownerId: String, path: Path, content: String, recovering: Boolean): Boolean {
        val old = if (Files.isRegularFile(path)) Files.size(path) else 0
        val bytes = content.toByteArray(Charsets.UTF_8).size.toLong()
        val next = (sizes[ownerId] ?: 0) - old + bytes
        if (!recovering && policy.maxStorageMb > 0 && next > policy.maxStorageMb * MEBIBYTE) {
            errors[ownerId] = "ACTIVITY_HISTORY_QUOTA_EXCEEDED"
            return false
        }
        atomic(path, content)
        sizes[ownerId] = next
        return true
    }

    private fun atomic(path: Path, content: String) {
        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temporary, content)
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun applyRetention(ownerId: String, keys: NavigableSet<String>) {
        if (policy.retentionDays == 0) return
        val cutoff = clock.instant().epochSecond - policy.retentionDays * SECONDS_PER_DAY
        keys.toList().stream().filter { it.substringBefore('-').toLong() < cutoff }.forEach { remove(ownerId, it) }
    }

    private fun remove(ownerId: String, key: String) {
        listOf("entry", "trace").stream().forEach { suffix ->
            val path = history(ownerId).resolve("$key.$suffix.json")
            if (Files.isRegularFile(path)) {
                val length = Files.size(path)
                Files.delete(path)
                sizes.compute(ownerId) { _, size -> (size ?: 0) - length }
            }
        }
        indexes.getValue(ownerId).remove(key)
    }

    private fun history(ownerId: String): Path = root.resolve(ownerId).resolve("history")
    private fun validOwner(value: String): Boolean = value.toLongOrNull()?.let { it > 0 } == true
    private fun validId(value: String): Boolean = try {
        UUID.fromString(value).toString() == value
    } catch (_: IllegalArgumentException) {
        false
    }
    private fun validKey(value: String): Boolean = value.length == 57 && value.take(20).all { it in '0'..'9' } &&
        value[20] == '-' && validId(value.drop(21))
    private fun encodeCursor(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
    private fun decodeCursor(value: String): String {
        if (value.length > 200) throw MalformedPayloadException()
        val key = try {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw MalformedPayloadException()
        }
        if (!validKey(key)) throw MalformedPayloadException()
        return key
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        ticker.cancel(false)
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Activity history writer is finishing pending disk operations")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            System.err.println("Activity history shutdown was interrupted")
        }
        flush()
    }
}
