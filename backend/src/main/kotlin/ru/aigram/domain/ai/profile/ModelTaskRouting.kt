package ru.aigram.domain.ai.profile

import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable

@Serializable
data class ModelTaskRouting(
    val enabled: Boolean = false,
    val parallelIndependentTasks: Boolean = false,
    @field:Size(max = 36) val replyProfileId: String = "",
    @field:Size(max = 36) val memoryProfileId: String = "",
    @field:Size(max = 36) val researchProfileId: String = "",
    @field:Size(max = 36) val visionProfileId: String = "",
    @field:Size(max = 120) val replyModel: String = "",
    @field:Size(max = 120) val memoryModel: String = "",
    @field:Size(max = 120) val researchModel: String = "",
    @field:Size(max = 120) val visionModel: String = "",
) {
    fun profileId(task: ModelTask): String = when (task) {
        ModelTask.REPLY -> replyProfileId
        ModelTask.MEMORY -> memoryProfileId
        ModelTask.RESEARCH -> researchProfileId
        ModelTask.VISION -> visionProfileId
    }

    fun model(task: ModelTask): String = when (task) {
        ModelTask.REPLY -> replyModel
        ModelTask.MEMORY -> memoryModel
        ModelTask.RESEARCH -> researchModel
        ModelTask.VISION -> visionModel
    }
}
