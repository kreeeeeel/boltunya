package ru.aigram.component.system

import org.springframework.stereotype.Component
import ru.aigram.monitoring.MonitoringService
import ru.aigram.telegram.TelegramCacheStorage

@Component
class ResourcesComponent(private val monitor: MonitoringService, private val cache: TelegramCacheStorage) {
    fun metrics() = monitor.history()
    fun cache() = cache.statistics()
    fun clearCache() = cache.clear()
}
