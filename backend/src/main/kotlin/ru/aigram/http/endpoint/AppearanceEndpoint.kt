package ru.aigram.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.springframework.stereotype.Component
import ru.aigram.domain.AppearanceSettings
import ru.aigram.storage.AppearanceStore

@Component
class AppearanceEndpoint @Inject constructor(private val store: AppearanceStore, private val json: Json) {
    fun get(): AppearanceSettings = store.get()
    fun update(body: JsonObject): AppearanceSettings = store.save(json.decodeFromJsonElement<AppearanceSettings>(body))
}
