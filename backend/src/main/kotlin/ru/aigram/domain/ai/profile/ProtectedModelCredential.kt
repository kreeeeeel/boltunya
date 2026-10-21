package ru.aigram.domain.ai.profile

import kotlinx.serialization.Serializable

@Serializable
internal data class ProtectedModelCredential(val origin: String, val apiKey: String)
