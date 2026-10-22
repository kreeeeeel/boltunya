package ru.aigram.transformer.system

import ru.aigram.contract.dto.CacheStatistics
import ru.aigram.domain.system.CacheStatistics as DomainCacheStatistics

fun DomainCacheStatistics.toContract(): CacheStatistics = CacheStatistics(
    totalBytes,
    fileCount,
    photoBytes,
    videoBytes,
    audioBytes,
    documentBytes,
)
