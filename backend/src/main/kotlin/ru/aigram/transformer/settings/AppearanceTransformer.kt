package ru.aigram.transformer.settings

import ru.aigram.contract.dto.AppearanceMode
import ru.aigram.contract.dto.AppearanceSettings
import ru.aigram.contract.dto.AppearanceSettingsInput
import ru.aigram.domain.settings.AppearanceSettings as DomainAppearanceSettings

fun DomainAppearanceSettings.toContract(): AppearanceSettings = AppearanceSettings(
    AppearanceMode.forValue(mode),
    tokens,
    css,
)

fun AppearanceSettingsInput.toDomain(): DomainAppearanceSettings = DomainAppearanceSettings(mode.value, tokens, css)
