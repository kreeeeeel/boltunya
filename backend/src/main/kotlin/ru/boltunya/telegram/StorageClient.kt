package ru.boltunya.telegram

import it.tdlight.jni.TdApi

fun interface StorageClient {
    fun execute(request: TdApi.Function<TdApi.StorageStatistics>): TdApi.StorageStatistics
}
