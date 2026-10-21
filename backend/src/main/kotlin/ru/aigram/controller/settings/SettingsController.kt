package ru.aigram.controller.settings

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.aigram.component.settings.AppearanceComponent
import ru.aigram.component.settings.SearchComponent
import ru.aigram.component.settings.SettingsComponent
import ru.aigram.contract.api.SettingsApi
import ru.aigram.contract.dto.AppearanceSettings
import ru.aigram.contract.dto.AppearanceSettingsInput
import ru.aigram.contract.dto.SearchConnection
import ru.aigram.contract.dto.SearchConnectionRequest
import ru.aigram.contract.dto.Settings
import ru.aigram.contract.dto.SettingsUpdate
import ru.aigram.transformer.settings.toContract
import ru.aigram.transformer.settings.toDomain

@RestController
class SettingsController(
    private val settings: SettingsComponent,
    private val appearance: AppearanceComponent,
    private val search: SearchComponent,
) : SettingsApi {
    override fun settings(settingsUpdate: SettingsUpdate): ResponseEntity<Settings> =
        ResponseEntity.ok(settings.update(settingsUpdate.toDomain()).toContract())

    override fun appearance(): ResponseEntity<AppearanceSettings> = ResponseEntity.ok(appearance.get().toContract())

    override fun saveAppearance(appearanceSettings: AppearanceSettingsInput): ResponseEntity<AppearanceSettings> =
        ResponseEntity.ok(
            appearance.save(appearanceSettings.toDomain()).toContract(),
        )

    override fun searchConnection(): ResponseEntity<SearchConnection> =
        ResponseEntity.ok(search.connection().toContract())

    override fun saveSearchKey(searchConnectionRequest: SearchConnectionRequest): ResponseEntity<SearchConnection> =
        ResponseEntity.ok(search.save(searchConnectionRequest.apiKey).toContract())

    override fun deleteSearchKey(): ResponseEntity<SearchConnection> = ResponseEntity.ok(search.delete().toContract())

    override fun checkSearchConnection(): ResponseEntity<SearchConnection> =
        ResponseEntity.ok(search.check().toContract())
}
