package ru.aigram.ai.prompt

import com.knuddels.jtokkit.api.EncodingType
import jakarta.inject.Inject
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import kotlin.math.ceil

private const val MESSAGE_OVERHEAD_TOKENS = 16
private const val ESTIMATION_MARGIN = 1.15
private const val IMAGE_TOKEN_ESTIMATE = 2048
private val cl100k by lazy { JTokkitTokenCountEstimator(EncodingType.CL100K_BASE) }
private val o200k by lazy { JTokkitTokenCountEstimator(EncodingType.O200K_BASE) }
private val O200K_MODELS = listOf("gpt-4o", "gpt-4.1", "gpt-5", "gpt-oss", "o1", "o3", "o4")

/** Text and media estimates reserve a safety margin; the provider's actual tokenizer remains authoritative. */
class PromptTokenEstimator @Inject constructor() {
    fun encoding(model: String, requested: TokenEncoding): TokenEncoding = if (requested != TokenEncoding.AUTO) {
        requested
    } else {
        if (O200K_MODELS.stream().anyMatch { model.substringAfterLast('/').startsWith(it) }) {
            TokenEncoding.O200K
        } else {
            TokenEncoding.CL100K
        }
    }

    fun estimate(message: Message, model: String, requested: TokenEncoding = TokenEncoding.AUTO): Int {
        val estimator = if (encoding(model, requested) == TokenEncoding.O200K) o200k else cl100k
        val images = if (message is UserMessage) message.media.size else 0
        return ceil(estimator.estimate(message.text.orEmpty()) * ESTIMATION_MARGIN).toInt() +
            MESSAGE_OVERHEAD_TOKENS + images * IMAGE_TOKEN_ESTIMATE
    }
}
