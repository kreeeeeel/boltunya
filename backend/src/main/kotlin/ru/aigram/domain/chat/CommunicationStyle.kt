package ru.aigram.domain.chat

enum class CommunicationStyle(val key: String) {
    FRIENDLY("friendly"),
    BUSINESS("business"),
    CONCISE("concise"),
    WARM("warm"),
    AGGRESSIVE("aggressive"),
    ROUGH("rough"),
    CUSTOM("custom"),
}
