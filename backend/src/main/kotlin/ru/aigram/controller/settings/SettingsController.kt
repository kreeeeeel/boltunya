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
import ru.aigram.transformer.settings.AppearanceTransformer
import ru.aigram.transformer.settings.SearchTransformer
import ru.aigram.transformer.settings.SettingsTransformer

@RestController
class SettingsController(
    private val settings: SettingsComponent,
    private val appearance: AppearanceComponent,
    private val search: SearchComponent,
    private val settingsTransformer: SettingsTransformer,
    private val appearanceTransformer: AppearanceTransformer,
    private val searchTransformer: SearchTransformer,
) : SettingsApi {
    override fun settings(settingsUpdate: SettingsUpdate): ResponseEntity<Settings> =
        ResponseEntity.ok(settingsTransformer.toContract(settings.update(settingsTransformer.toDomain(settingsUpdate))))

    override fun appearance(): ResponseEntity<AppearanceSettings> =
        ResponseEntity.ok(appearanceTransformer.toContract(appearance.get()))

    override fun saveAppearance(appearanceSettings: AppearanceSettingsInput): ResponseEntity<AppearanceSettings> =
        ResponseEntity.ok(
            appearanceTransformer.toContract(appearance.save(appearanceTransformer.toDomain(appearanceSettings))),
        )

    override fun searchConnection(): ResponseEntity<SearchConnection> =
        ResponseEntity.ok(searchTransformer.toContract(search.connection()))

    override fun saveSearchKey(searchConnectionRequest: SearchConnectionRequest): ResponseEntity<SearchConnection> =
        ResponseEntity.ok(searchTransformer.toContract(search.save(searchConnectionRequest.apiKey)))

    override fun deleteSearchKey(): ResponseEntity<SearchConnection> =
        ResponseEntity.ok(searchTransformer.toContract(search.delete()))

    override fun checkSearchConnection(): ResponseEntity<SearchConnection> =
        ResponseEntity.ok(searchTransformer.toContract(search.check()))
}
