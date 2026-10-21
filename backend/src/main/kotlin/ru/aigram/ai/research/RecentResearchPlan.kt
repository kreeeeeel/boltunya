package ru.aigram.ai.research

import java.time.Instant

data class RecentResearchPlan(val messageId: String, val plan: ResearchPlan, val recordedAt: Instant)
