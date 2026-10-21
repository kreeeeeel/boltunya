package ru.aigram.ai.profile

import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.ai.profile.ModelProfile
import ru.aigram.domain.ai.profile.ModelProfileUpdate
import ru.aigram.domain.ai.profile.ModelProvider
import ru.aigram.domain.ai.profile.ModelProviderPreset
import ru.aigram.domain.ai.profile.ResolvedModelTarget
import ru.aigram.exception.impl.InvalidModelProfileException
import ru.aigram.exception.impl.ModelCredentialMissingException
import ru.aigram.exception.impl.ModelProfileChangedException
import ru.aigram.exception.impl.ModelProfileDisabledException
import ru.aigram.exception.impl.ModelProfileNotFoundException
import ru.aigram.resources.ResourceCatalog
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

private const val MAX_PROFILES = 40

@Component
class ModelProfileStore(
    directory: Path,
    private val vault: ModelCredentialVault,
    private val resources: ResourceCatalog,
) {
    private val file = directory.resolve("models/profiles.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile private var profiles: List<ModelProfile> = if (Files.isRegularFile(file)) {
        json.decodeFromString(Files.readString(file))
    } else {
        emptyList()
    }

    fun list(): List<ModelProfile> = profiles.stream().map { it.copy(apiKeyConfigured = vault.exists(it.id)) }.toList()

    fun get(id: String): ModelProfile {
        val profile = profiles.stream().filter { it.id == id }.findFirst().orElse(null)
        if (profile == null) {
            throw ModelProfileNotFoundException()
        }
        return profile.copy(apiKeyConfigured = vault.exists(id))
    }

    fun presets(): List<ModelProviderPreset> = json.decodeFromString(resources.text("models/providers.json"))

    /** Captures credentials under the destination-edit lock, preventing new keys from reaching a stale host. */
    @Synchronized
    internal fun credentialSnapshot(target: ResolvedModelTarget): String {
        val profile = get(target.profileId)
        if (!profile.enabled) {
            throw ModelProfileDisabledException()
        }
        if (profile.provider != target.provider || profile.baseUrl != target.baseUrl || profile.model != target.model ||
            profile.options != target.options || profile.capabilities != target.capabilities
        ) {
            throw ModelProfileChangedException()
        }
        val key = vault.load(profile.id, origin(profile.baseUrl))
        if (profile.provider == ModelProvider.OPENAI_COMPATIBLE && key.isEmpty() &&
            presets().stream().anyMatch { it.id == profile.preset && it.apiKeyRequired }
        ) {
            throw ModelCredentialMissingException()
        }
        return key
    }

    @Synchronized
    fun save(id: String?, update: ModelProfileUpdate): ModelProfile {
        val previous = if (id == null) {
            null
        } else {
            get(id)
        }
        if (previous == null && profiles.size >= MAX_PROFILES) {
            throw InvalidModelProfileException()
        }
        val baseUrl = validateUrl(update.baseUrl)
        if (presets().stream().noneMatch { it.id == update.preset }) {
            throw InvalidModelProfileException()
        }
        if (update.options.stop.stream().anyMatch { it.length > 200 }) {
            throw InvalidModelProfileException()
        }
        if (update.options.maxOutputTokens >= update.options.contextWindow) {
            throw InvalidModelProfileException()
        }
        if (update.provider == ModelProvider.OLLAMA && update.preset != "ollama" && update.preset != "custom") {
            throw InvalidModelProfileException()
        }
        val key = update.apiKey?.trim().orEmpty()
        if (key.isNotEmpty() &&
            key.codePoints().anyMatch { Character.isWhitespace(it) || Character.isISOControl(it) }
        ) {
            throw InvalidModelProfileException()
        }
        val identifier = previous?.id ?: UUID.randomUUID().toString()
        val next = ModelProfile(
            identifier, update.name.trim(), update.provider, update.preset, baseUrl, update.model.trim(),
            update.enabled, update.options, update.capabilities,
        )
        // A stored credential must never silently travel to another host after editing a profile.
        val changedOrigin = previous != null && origin(previous.baseUrl) != origin(baseUrl)
        if (update.clearApiKey || changedOrigin) {
            vault.delete(identifier)
        }
        if (key.isNotEmpty()) {
            vault.save(identifier, key, origin(baseUrl))
        }
        val entries = profiles.stream().filter { it.id != identifier }.toList() + next
        persist(entries)
        return next.copy(apiKeyConfigured = vault.exists(identifier))
    }

    @Synchronized
    fun delete(id: String) {
        get(id)
        persist(profiles.stream().filter { it.id != id }.toList())
        vault.delete(id)
    }

    private fun persist(next: List<ModelProfile>) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("profiles.tmp")
        Files.writeString(temporary, json.encodeToString(next))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        profiles = next
    }

    private fun validateUrl(value: String): String {
        val uri = try {
            URI(value.trim())
        } catch (exception: Exception) {
            throw InvalidModelProfileException(exception)
        }
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null || uri.port < -1 || uri.port > 65535
        ) {
            throw InvalidModelProfileException()
        }
        return uri.toASCIIString().trimEnd('/')
    }

    private fun origin(value: String): String {
        val uri = URI(value)
        return "${uri.scheme}://${uri.host}:${uri.port}"
    }
}
