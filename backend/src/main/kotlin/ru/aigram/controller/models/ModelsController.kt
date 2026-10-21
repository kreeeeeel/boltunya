package ru.aigram.controller.models

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.aigram.component.models.DraftComponent
import ru.aigram.component.models.EngineComponent
import ru.aigram.component.models.ModelProfilesComponent
import ru.aigram.component.models.ModelPromptsComponent
import ru.aigram.component.models.PreviewComponent
import ru.aigram.contract.api.ModelsApi
import ru.aigram.contract.dto.ComputeDeviceStatus
import ru.aigram.contract.dto.ModelProfile
import ru.aigram.contract.dto.ModelProfileReport
import ru.aigram.contract.dto.ModelProfileTestRequest
import ru.aigram.contract.dto.ModelProfileTestResult
import ru.aigram.contract.dto.ModelProfileUpdate
import ru.aigram.contract.dto.ModelPromptReport
import ru.aigram.contract.dto.ModelPromptSettings
import ru.aigram.contract.dto.ModelPromptSettingsInput
import ru.aigram.contract.dto.ModelRequest
import ru.aigram.contract.dto.ModelTaskRouting
import ru.aigram.contract.dto.Ok
import ru.aigram.contract.dto.Preview
import ru.aigram.contract.dto.PreviewJob
import ru.aigram.contract.dto.PreviewJobRequest
import ru.aigram.contract.dto.PromptBudgetReport
import ru.aigram.contract.dto.PromptBudgetRequest
import ru.aigram.contract.dto.Reply
import ru.aigram.domain.speech.DraftOutput
import ru.aigram.transformer.chat.toDomain
import ru.aigram.transformer.models.toContract
import ru.aigram.transformer.models.toDomain
import java.util.UUID

@RestController
class ModelsController(
    private val engine: EngineComponent,
    private val profiles: ModelProfilesComponent,
    private val prompts: ModelPromptsComponent,
    private val previews: PreviewComponent,
    private val drafts: DraftComponent,
) : ModelsApi {
    override fun modelProfiles(): ResponseEntity<ModelProfileReport> = ResponseEntity.ok(profiles.list().toContract())

    override fun createModelProfile(modelProfileUpdate: ModelProfileUpdate): ResponseEntity<ModelProfile> =
        ResponseEntity.ok(
            profiles.save(null, modelProfileUpdate.toDomain()).toContract(),
        )

    override fun updateModelProfile(id: UUID, modelProfileUpdate: ModelProfileUpdate): ResponseEntity<ModelProfile> =
        ResponseEntity.ok(
            profiles.save(id.toString(), modelProfileUpdate.toDomain()).toContract(),
        )

    override fun deleteModelProfile(id: UUID): ResponseEntity<Ok> {
        profiles.delete(id.toString())
        return ResponseEntity.ok(Ok(true))
    }

    override fun testModelProfile(
        id: UUID,
        modelProfileTestRequest: ModelProfileTestRequest,
    ): ResponseEntity<ModelProfileTestResult> = ResponseEntity.ok(
        profiles.test(id.toString(), modelProfileTestRequest.confirmGeneration == true).toContract(),
    )

    override fun modelRouting(): ResponseEntity<ModelTaskRouting> = ResponseEntity.ok(profiles.routing().toContract())

    override fun saveModelRouting(modelTaskRouting: ModelTaskRouting): ResponseEntity<ModelTaskRouting> =
        ResponseEntity.ok(
            profiles.routing(modelTaskRouting.toDomain()).toContract(),
        )

    override fun getModelPrompts(): ResponseEntity<ModelPromptReport> = ResponseEntity.ok(prompts.list().toContract())

    override fun saveModelPrompts(modelPromptSettings: ModelPromptSettingsInput): ResponseEntity<ModelPromptSettings> =
        ResponseEntity.ok(prompts.save(modelPromptSettings.toDomain()).toContract())

    override fun estimatePromptBudget(promptBudgetRequest: PromptBudgetRequest): ResponseEntity<PromptBudgetReport> =
        ResponseEntity.ok(prompts.budget(promptBudgetRequest.toDomain()).toContract())

    override fun engineDevices(): ResponseEntity<ComputeDeviceStatus> = ResponseEntity.ok(engine.devices().toContract())

    override fun preview(preview: Preview): ResponseEntity<Reply> = ResponseEntity.ok(
        previews.reply(preview.rule.toDomain(), preview.chatId.orEmpty(), preview.text).toContract(),
    )

    override fun enqueuePreviewJob(previewJobRequest: PreviewJobRequest): ResponseEntity<PreviewJob> =
        ResponseEntity.ok(
            drafts.enqueue(
                previewJobRequest.chatId,
                previewJobRequest.rule?.toDomain(),
                previewJobRequest.messageId.orEmpty(),
                previewJobRequest.output?.let { DraftOutput.valueOf(it.name) } ?: DraftOutput.TEXT,
            ).toContract(),
        )

    override fun previewJob(id: String): ResponseEntity<PreviewJob> = ResponseEntity.ok(drafts.job(id).toContract())

    override fun install(): ResponseEntity<Ok> {
        engine.install()
        return ResponseEntity.ok(Ok(true))
    }

    override fun start(): ResponseEntity<Ok> {
        engine.start()
        return ResponseEntity.ok(Ok(true))
    }

    override fun restartEngine(): ResponseEntity<Ok> {
        engine.restart()
        return ResponseEntity.ok(Ok(true))
    }

    override fun pull(modelRequest: ModelRequest): ResponseEntity<Ok> {
        engine.pull(modelRequest.model)
        return ResponseEntity.ok(Ok(true))
    }

    override fun cancelModelDownload(): ResponseEntity<Ok> {
        engine.cancelDownload()
        return ResponseEntity.ok(Ok(true))
    }
}
