package ru.aigram.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import jakarta.inject.Provider
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private const val STORAGE_TIMEOUT_SECONDS = 60L

@Component
class TdLightStorageClient @Inject constructor(private val gateway: Provider<TdLightGateway>) : StorageClient {
    override fun execute(request: TdApi.Function<TdApi.StorageStatistics>): TdApi.StorageStatistics =
        gateway.get().readyClient().send(request).get(STORAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
}
