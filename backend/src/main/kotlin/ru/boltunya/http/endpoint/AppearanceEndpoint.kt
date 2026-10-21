package ru.boltunya.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import ru.boltunya.domain.AppearanceSettings
import ru.boltunya.storage.AppearanceStore

class AppearanceEndpoint @Inject constructor(private val store: AppearanceStore, private val json: Json) {
    fun get(): AppearanceSettings = store.get()
    fun update(body: JsonObject): AppearanceSettings = store.save(json.decodeFromJsonElement<AppearanceSettings>(body))
}
