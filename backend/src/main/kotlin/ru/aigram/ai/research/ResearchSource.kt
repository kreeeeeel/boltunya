package ru.aigram.ai.research

import kotlinx.serialization.Serializable

@Serializable
data class ResearchSource(val title: String, val url: String, val text: String, val publishedAt: String = "")
