package ru.aigram.transformer.settings

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.AppearanceMode
import ru.aigram.contract.dto.AppearanceSettings
import ru.aigram.contract.dto.AppearanceSettingsInput
import ru.aigram.domain.settings.AppearanceSettings as DomainAppearanceSettings

@Component
class AppearanceTransformer {
    fun toContract(value: DomainAppearanceSettings): AppearanceSettings = AppearanceSettings(
        AppearanceMode.forValue(value.mode),
        value.tokens,
        value.css,
    )

    fun toDomain(value: AppearanceSettingsInput): DomainAppearanceSettings =
        DomainAppearanceSettings(value.mode.value, value.tokens, value.css)
}
