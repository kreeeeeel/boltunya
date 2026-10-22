package ru.aigram.component.memory

import jakarta.validation.Validation
import jakarta.validation.Validator
import ru.aigram.ai.ModelCatalog
import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaRuntime
import ru.aigram.ai.profile.ModelCredentialVault
import ru.aigram.ai.profile.ModelProfileStore
import ru.aigram.ai.profile.ModelRoutingStore
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.automation.MemoryWorkQueue
import ru.aigram.configuration.ApplicationConfiguration
import ru.aigram.configuration.testBeanProvider
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.memory.MemoryBatchBuilder
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.memory.MemoryProcessor
import ru.aigram.memory.MemoryStore
import ru.aigram.memory.MemorySummarizer
import ru.aigram.memory.MemoryTelegramGateway
import ru.aigram.memory.OwnerMemorySummarizer
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway
import ru.aigram.validation.InputValidation
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.time.Clock

class MemoryNoteOwnershipFixture(directory: Path) : AutoCloseable {
    private val resources = ResourceCatalog()
    private val native = MemoryTelegramGateway()
    private val factory = Validation.buildDefaultValidatorFactory()
    private val settings = SettingsStore(directory)
    private val models = ModelCatalog()
    private val targets = ModelTargetResolver(
        ModelProfileStore(directory, ModelCredentialVault(directory), resources),
        ModelRoutingStore(directory),
        settings,
        ApplicationConfiguration(directory, false, "http://127.0.0.1:1"),
        models,
    )

    @Volatile var beforeValidation: () -> Unit = {}

    @Volatile var afterStatus: (TelegramStatus) -> Unit = {}

    var ownerId: String
        get() = native.ownerId
        set(value) {
            native.ownerId = value
        }

    val store = MemoryStore(directory, resources)
    private val telegram = Proxy.newProxyInstance(
        TelegramGateway::class.java.classLoader,
        arrayOf(TelegramGateway::class.java),
    ) { _, method, arguments ->
        val result = try {
            method.invoke(native, *arguments.orEmpty())
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        }
        if (method.name == "status") {
            afterStatus(result as TelegramStatus)
        }
        result
    } as TelegramGateway
    private val validator = Proxy.newProxyInstance(
        Validator::class.java.classLoader,
        arrayOf(Validator::class.java),
    ) { _, method, arguments ->
        if (method.name == "validate") {
            beforeValidation()
        }
        try {
            method.invoke(factory.validator, *arguments.orEmpty())
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        }
    } as Validator
    private val automated = AutomatedReplyStore(directory)
    private val memory = MemoryCoordinator(
        store,
        settings,
        telegram,
        OllamaRuntime("http://127.0.0.1:1"),
        MemoryProcessor(
            telegram,
            store,
            MemoryBatchBuilder(automated),
            MemorySummarizer { _, _, _ -> error("Note editing must not run inference") },
            Clock.systemUTC(),
            OwnerMemorySummarizer { _, _, _ -> error("Note editing must not run owner inference") },
        ),
        ModelInference(),
        Clock.systemUTC(),
        automated,
        testBeanProvider<MemoryWorkQueue> { error("Note editing must not access the work queue") },
    )
    val component = MemoryComponent(memory, store, telegram, InputValidation(models, targets, validator))

    fun prepare(ownerId: String, notes: String) {
        this.ownerId = ownerId
        store.activate(ownerId)
        store.observe("42")
        store.edit("42", notes, store.read("42").revision)
    }

    override fun close() {
        memory.close()
        factory.close()
    }
}
