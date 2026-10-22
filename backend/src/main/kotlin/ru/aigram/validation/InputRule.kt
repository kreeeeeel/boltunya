package ru.aigram.validation

import java.util.UUID

enum class InputRule {
    COMMUNICATION_STYLE,
    STYLE_ID,
    CREDENTIALS_HASH,
    CREDENTIALS_PHONE,
    CHAT_ID,
    GPU_UUID,
    MODEL_DIGEST,
    ;

    fun accepts(value: String): Boolean = when (this) {
        COMMUNICATION_STYLE ->
            value in
                setOf("friendly", "business", "concise", "warm", "aggressive", "rough", "custom")
        STYLE_ID ->
            value.length in 1..64 &&
                value.chars().allMatch { asciiLetter(it) || digit(it) || it == '-'.code }
        CREDENTIALS_HASH -> value.length == 32 && value.chars().allMatch(::hex)
        CREDENTIALS_PHONE -> value.startsWith('+') && value.length in 8..16 &&
            value.substring(1).chars().allMatch(::digit)
        CHAT_ID -> {
            val digits = value.removePrefix("-")
            digits.isNotEmpty() && digits[0] in '1'..'9' && digits.chars().allMatch(::digit) &&
                value.toLongOrNull() != null
        }
        GPU_UUID -> value.startsWith("GPU-", ignoreCase = true) && canonicalUuid(value.substring(4))
        MODEL_DIGEST -> value.startsWith("sha256:") && value.length == 71 &&
            value.substring(7).chars().allMatch { digit(it) || it in 'a'.code..'f'.code }
    }

    private fun asciiLetter(value: Int): Boolean = value in 'a'.code..'z'.code || value in 'A'.code..'Z'.code

    private fun digit(value: Int): Boolean = value in '0'.code..'9'.code

    private fun hex(value: Int): Boolean = digit(value) || value in 'a'.code..'f'.code || value in 'A'.code..'F'.code

    private fun canonicalUuid(value: String): Boolean = try {
        UUID.fromString(value).toString().equals(value, ignoreCase = true)
    } catch (_: IllegalArgumentException) {
        false
    }
}
