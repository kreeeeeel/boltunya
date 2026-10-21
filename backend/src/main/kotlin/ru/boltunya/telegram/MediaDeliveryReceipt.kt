package ru.boltunya.telegram

import java.util.concurrent.CompletableFuture

internal data class MediaDeliveryReceipt(val createdAt: Long, val result: CompletableFuture<Long> = CompletableFuture())
