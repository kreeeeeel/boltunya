package ru.aigram.ai.profile

import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import ru.aigram.ai.model.SpringAiModelFactory
import ru.aigram.domain.ai.profile.ModelProvider
import ru.aigram.domain.ai.profile.ResolvedModelTarget
import ru.aigram.exception.impl.ModelProviderFailedException
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Component
class ProfileModelFactory(private val profiles: ModelProfileStore, private val ollama: SpringAiModelFactory) :
    AutoCloseable {
    private val closed = AtomicBoolean()
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val clients = ConcurrentHashMap<String, ProfileModelClient>()

    fun model(target: ResolvedModelTarget): ChatModel {
        val key = profiles.credentialSnapshot(target)
        // A digest detects rotations without retaining the credential as a map key or diagnostic field.
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest((target.toString() + key).toByteArray(Charsets.UTF_8)).contentToString()
        val client = clients.compute(target.profileId) { _, previous ->
            if (previous?.fingerprint == fingerprint) {
                previous
            } else {
                ProfileModelClient(fingerprint, create(target, key))
            }
        }
        if (client == null) {
            throw ModelProviderFailedException()
        }
        return client.model
    }

    private fun create(target: ResolvedModelTarget, key: String): ChatModel {
        if (target.provider == ModelProvider.OLLAMA) {
            return ollama.create(target.baseUrl, target.options.connectTimeoutSeconds)
        }
        val options = OpenAiChatOptions.builder()
            .baseUrl(target.baseUrl).apiKey(key).model(target.model).maxRetries(0)
            .timeout(Duration.ofSeconds(target.options.totalTimeoutSeconds.toLong())).build()
        return OpenAiChatModel.builder().options(options).httpClientBuilderCustomizer { builder ->
            builder.dispatcherExecutorService(executor).interceptor { chain ->
                chain.withConnectTimeout(target.options.connectTimeoutSeconds, TimeUnit.SECONDS)
                    .withReadTimeout(target.options.idleTimeoutSeconds, TimeUnit.SECONDS)
                    .proceed(chain.request())
            }
        }.build()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        clients.clear()
        executor.shutdownNow()
    }
}
