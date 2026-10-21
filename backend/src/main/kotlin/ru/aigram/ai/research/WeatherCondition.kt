package ru.aigram.ai.research

enum class WeatherCondition(val codes: Set<Int>, val resourceKey: String) {
    CLEAR(setOf(0), "conditions-clear"),
    CLOUDS(setOf(1, 2), "conditions-clouds"),
    OVERCAST(setOf(3), "conditions-overcast"),
    FOG(setOf(45, 48), "conditions-fog"),
    DRIZZLE(setOf(51, 53, 55, 56, 57), "conditions-drizzle"),
    RAIN(setOf(61, 63, 65, 66, 67, 80, 81, 82), "conditions-rain"),
    SNOW(setOf(71, 73, 75, 77, 85, 86), "conditions-snow"),
    STORM(setOf(95, 96, 99), "conditions-storm"),
    UNKNOWN(emptySet(), "conditions-unknown"),
}
