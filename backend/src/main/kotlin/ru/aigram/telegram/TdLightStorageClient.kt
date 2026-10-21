package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private const val STORAGE_TIMEOUT_SECONDS = 60L

@Component
class TdLightStorageClient(private val gateway: ObjectProvider<TdLightGateway>) : StorageClient {
    override fun execute(request: TdApi.Function<TdApi.StorageStatistics>): TdApi.StorageStatistics =
        gateway.getObject().readyClient().send(request).get(STORAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
}
