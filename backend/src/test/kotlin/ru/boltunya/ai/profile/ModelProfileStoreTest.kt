package ru.boltunya.ai.profile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.exception.impl.InvalidModelProfileException
import ru.boltunya.exception.impl.ModelCredentialMissingException
import ru.boltunya.exception.impl.ModelProfileChangedException
import ru.boltunya.resources.ResourceCatalog
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelProfileStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `failed metadata write cannot expose a rotated credential to the previous host after restart`() {
        val vault = ModelCredentialVault(directory)
        val store = ModelProfileStore(directory, vault, ResourceCatalog())
        val update = ModelProfileUpdate(
            "API",
            ModelProvider.OPENAI_COMPATIBLE,
            baseUrl = "https://first.test/v1",
            model = "test",
            apiKey = "first-provider-key",
        )
        val profile = store.save(null, update)
        Files.createDirectory(directory.resolve("models/profiles.tmp"))
        assertFailsWith<IOException> {
            store.save(profile.id, update.copy(baseUrl = "https://second.test/v1", apiKey = "second-provider-key"))
        }
        val reopened = ModelProfileStore(directory, ModelCredentialVault(directory), ResourceCatalog())
        assertEquals("https://first.test/v1", reopened.get(profile.id).baseUrl)
        val previous = ResolvedModelTarget(
            profile.id,
            profile.provider,
            profile.model,
            profile.baseUrl,
            true,
            profile.capabilities,
            profile.options,
        )
        assertFailsWith<ModelCredentialMissingException> { store.credentialSnapshot(previous) }
        assertFailsWith<ModelCredentialMissingException> { reopened.credentialSnapshot(previous) }
        assertEquals("second-provider-key", vault.load(profile.id, "https://second.test:-1"))
    }

    @Test
    fun `destination rotation cannot pair a new provider credential with an old request target`() {
        val store = ModelProfileStore(directory, ModelCredentialVault(directory), ResourceCatalog())
        val update = ModelProfileUpdate(
            "API",
            ModelProvider.OPENAI_COMPATIBLE,
            baseUrl = "https://first.test/v1",
            model = "test",
            apiKey = "first-provider-key",
        )
        val profile = store.save(null, update)
        val first = ResolvedModelTarget(
            profile.id,
            profile.provider,
            profile.model,
            profile.baseUrl,
            true,
            profile.capabilities,
            profile.options,
        )
        val capturedBeforeRotation = store.credentialSnapshot(first)
        store.save(profile.id, update.copy(baseUrl = "https://second.test/v1", apiKey = "second-provider-key"))
        assertEquals("first-provider-key", capturedBeforeRotation)
        assertFailsWith<ModelProfileChangedException> { store.credentialSnapshot(first) }
        assertEquals("second-provider-key", store.credentialSnapshot(first.copy(baseUrl = "https://second.test/v1")))
    }

    @Test
    fun `credentials persist through DPAPI separately from public metadata and can be cleared`() {
        val vault = ModelCredentialVault(directory)
        val profiles = ModelProfileStore(directory, vault, ResourceCatalog())
        val update =
            ModelProfileUpdate(
                "My API",
                ModelProvider.OPENAI_COMPATIBLE,
                baseUrl = "https://example.test/v1",
                model = "test",
                apiKey = "secret-test-key",
            )
        val saved = profiles.save(null, update)
        assertTrue(saved.apiKeyConfigured)
        assertEquals("secret-test-key", vault.load(saved.id, "https://example.test:-1"))
        assertFalse(Files.readString(directory.resolve("models/profiles.json")).contains("secret-test-key"))
        assertFalse(
            String(
                Files.readAllBytes(directory.resolve("models/credentials/${saved.id}.credentials")),
            ).contains("secret-test-key"),
        )
        val reopened = ModelProfileStore(directory, ModelCredentialVault(directory), ResourceCatalog())
        assertTrue(reopened.get(saved.id).apiKeyConfigured)
        reopened.save(saved.id, update.copy(apiKey = ""))
        assertEquals("secret-test-key", vault.load(saved.id, "https://example.test:-1"))
        reopened.save(saved.id, update.copy(apiKey = null, clearApiKey = true))
        assertFalse(reopened.get(saved.id).apiKeyConfigured)
    }

    @Test
    fun `changing destination drops previous credential rather than leaking it to another server`() {
        val vault = ModelCredentialVault(directory)
        val store = ModelProfileStore(directory, vault, ResourceCatalog())
        val update =
            ModelProfileUpdate(
                "API",
                ModelProvider.OPENAI_COMPATIBLE,
                baseUrl = "https://first.test/v1",
                model = "test",
                apiKey = "secret",
            )
        val saved = store.save(null, update)
        store.save(saved.id, update.copy(baseUrl = "https://second.test/v1", apiKey = null))
        assertFalse(vault.exists(saved.id))
    }

    @Test
    fun `rejects credentials and query parameters inside destination URLs`() {
        val store = ModelProfileStore(directory, ModelCredentialVault(directory), ResourceCatalog())
        listOf(
            "https://user:secret@example.test/v1",
            "https://example.test/v1?api_key=secret",
            "file:///tmp/model",
            "https://example.test/#secret",
        ).stream().forEach { url ->
            assertFailsWith<InvalidModelProfileException> {
                store.save(
                    null,
                    ModelProfileUpdate("Bad URL", ModelProvider.OPENAI_COMPATIBLE, baseUrl = url, model = "test"),
                )
            }
        }
    }

    @Test
    fun `vault rejects path traversal and noncanonical identifiers`() {
        val vault = ModelCredentialVault(directory)
        assertFailsWith<InvalidModelProfileException> { vault.load("../credentials", "https://example.test:-1") }
        assertFailsWith<InvalidModelProfileException> { vault.load("1-1-1-1-1", "https://example.test:-1") }
    }
}
