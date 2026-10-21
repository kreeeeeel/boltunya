package ru.boltunya.ai.profile

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
) {
    fun profileId(task: ModelTask): String = when (task) {
        ModelTask.REPLY -> replyProfileId
        ModelTask.MEMORY -> memoryProfileId
        ModelTask.RESEARCH -> researchProfileId
        ModelTask.VISION -> visionProfileId
    }
}
