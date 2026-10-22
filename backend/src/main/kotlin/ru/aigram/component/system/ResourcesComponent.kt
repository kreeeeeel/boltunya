package ru.aigram.component.system

import org.springframework.stereotype.Component
import ru.aigram.telegram.TelegramCacheStorage

@Component
class ResourcesComponent(private val cache: TelegramCacheStorage) {
    fun cache() = cache.statistics()
    fun clearCache() = cache.clear()
}
