package ru.aigram.controller.settings

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.aigram.component.settings.SettingsComponent
import ru.aigram.contract.api.SettingsApi
import ru.aigram.contract.dto.Settings
import ru.aigram.contract.dto.SettingsUpdate
import ru.aigram.transformer.settings.toContract
import ru.aigram.transformer.settings.toDomain

@RestController
class SettingsController(private val settings: SettingsComponent) : SettingsApi {
    override fun settings(settingsUpdate: SettingsUpdate): ResponseEntity<Settings> =
        ResponseEntity.ok(settings.update(settingsUpdate.toDomain()).toContract())
}
