package ru.aigram.domain.ai.profile

import ru.aigram.domain.ai.prompt.TokenEncoding

/** Safe request target: deliberately contains no credential. */
data class ResolvedModelTarget(
    val profileId: String = "",
    val provider: ModelProvider = ModelProvider.OLLAMA,
    val model: String,
    val baseUrl: String,
    val remote: Boolean,
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val options: ModelProfileOptions = ModelProfileOptions(),
    val executionGroup: String = "shared",
    val tokenEncoding: TokenEncoding = TokenEncoding.AUTO,
    val automaticContext: Boolean = false,
)
