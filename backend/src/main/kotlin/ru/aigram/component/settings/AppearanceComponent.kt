package ru.aigram.component.settings

import org.springframework.stereotype.Component
import ru.aigram.domain.settings.AppearanceSettings
import ru.aigram.storage.AppearanceStore

@Component
class AppearanceComponent(private val store: AppearanceStore) {
    fun get(): AppearanceSettings = store.get()
    fun save(value: AppearanceSettings): AppearanceSettings = store.save(value)
}
