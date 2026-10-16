package ru.boltunya.ai.model

import jakarta.inject.Inject
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.core.retry.RetryPolicy
import org.springframework.core.retry.RetryTemplate
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

class SpringAiModelFactory @Inject constructor() {
    fun create(baseUrl: String): ChatModel {
        val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
        val requestFactory = JdkClientHttpRequestFactory(http)
        requestFactory.setReadTimeout(Duration.ofMinutes(3))
        val api = OllamaApi.builder().baseUrl(baseUrl)
            .restClientBuilder(RestClient.builder().requestFactory(requestFactory)).build()
        return OllamaChatModel.builder().ollamaApi(api)
            .retryTemplate(RetryTemplate(RetryPolicy.withMaxRetries(0))).build()
    }
}
