package ru.boltunya.ai.profile

import org.springframework.ai.chat.model.ChatModel

data class ProfileModelClient(val fingerprint: String, val model: ChatModel)
