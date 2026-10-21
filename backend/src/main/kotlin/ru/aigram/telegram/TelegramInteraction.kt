package ru.aigram.telegram

import it.tdlight.client.ClientInteraction
import it.tdlight.client.InputParameter
import it.tdlight.client.ParameterInfo
import ru.aigram.domain.TelegramStatus
import ru.aigram.exception.InvalidInputException
import ru.aigram.exception.impl.AuthenticationCodeNotExpectedException
import ru.aigram.exception.impl.UnsupportedAuthenticationException
import java.util.concurrent.CompletableFuture

class TelegramInteraction(private val onStatus: (TelegramStatus) -> Unit) : ClientInteraction {
    private var pending: CompletableFuture<String>? = null

    @Synchronized
    override fun onParameterRequest(
        parameter: InputParameter,
        parameterInfo: ParameterInfo,
    ): CompletableFuture<String> {
        val state = when (parameter) {
            InputParameter.ASK_CODE -> "code"
            InputParameter.ASK_PASSWORD -> "password"
            InputParameter.ASK_EMAIL_ADDRESS -> "email"
            InputParameter.ASK_EMAIL_CODE -> "emailCode"
            else -> "unsupported"
        }
        val future = CompletableFuture<String>()
        pending = future
        onStatus(TelegramStatus(state = state))
        if (state == "unsupported") {
            onStatus(
                TelegramStatus(
                    state = "error",
                    error = UnsupportedAuthenticationException().message,
                ),
            )
            future.completeExceptionally(UnsupportedAuthenticationException())
        }
        return future
    }

    @Synchronized
    fun submit(value: String) {
        val request = pending
        if (request == null || request.isDone) {
            throw AuthenticationCodeNotExpectedException()
        }
        pending = null
        request.complete(value)
    }

    @Synchronized
    fun cancel() {
        pending?.cancel(false)
        pending = null
    }
}
