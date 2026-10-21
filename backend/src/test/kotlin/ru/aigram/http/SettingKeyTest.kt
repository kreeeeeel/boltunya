package ru.aigram.http

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingKeyTest {
    @Test
    fun `accepts each wire type and rejects string coercion and fractional delays`() {
        assertTrue(SettingKey.PAUSED.accepts(Json.parseToJsonElement("true")))
        assertFalse(SettingKey.PAUSED.accepts(Json.parseToJsonElement("\"true\"")))
        assertFalse(SettingKey.PAUSED.accepts(Json.parseToJsonElement("null")))
        assertTrue(SettingKey.REPLY_DELAY_SECONDS.accepts(Json.parseToJsonElement("4")))
        assertFalse(SettingKey.REPLY_DELAY_SECONDS.accepts(Json.parseToJsonElement("4.5")))
        assertFalse(SettingKey.REPLY_DELAY_SECONDS.accepts(Json.parseToJsonElement("\"4\"")))
        assertTrue(SettingKey.GLOBAL_PROMPT.accepts(Json.parseToJsonElement("\"hello\"")))
        assertFalse(SettingKey.GLOBAL_PROMPT.accepts(Json.parseToJsonElement("{}")))
        assertTrue(SettingKey.CUSTOM_STYLES.accepts(Json.parseToJsonElement("[]")))
        assertFalse(SettingKey.CUSTOM_STYLES.accepts(Json.parseToJsonElement("{}")))
    }
}
