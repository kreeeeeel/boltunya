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
import ru.aigram.transformer.chat.ChatRuleTransformer
import ru.aigram.transformer.models.EngineTransformer
import ru.aigram.transformer.models.ModelProfileTransformer
import ru.aigram.transformer.models.ModelPromptTransformer
import ru.aigram.transformer.models.PreviewTransformer
import java.util.UUID

@RestController
class ModelsController(
    private val engine: EngineComponent,
    private val profiles: ModelProfilesComponent,
    private val prompts: ModelPromptsComponent,
    private val previews: PreviewComponent,
    private val drafts: DraftComponent,
    private val engineTransformer: EngineTransformer,
    private val profileTransformer: ModelProfileTransformer,
    private val promptTransformer: ModelPromptTransformer,
    private val previewTransformer: PreviewTransformer,
    private val rules: ChatRuleTransformer,
) : ModelsApi {
    override fun modelProfiles(): ResponseEntity<ModelProfileReport> =
        ResponseEntity.ok(profileTransformer.toContract(profiles.list()))

    override fun createModelProfile(modelProfileUpdate: ModelProfileUpdate): ResponseEntity<ModelProfile> =
        ResponseEntity.ok(
            profileTransformer.toContract(profiles.save(null, profileTransformer.toDomain(modelProfileUpdate))),
        )

    override fun updateModelProfile(id: UUID, modelProfileUpdate: ModelProfileUpdate): ResponseEntity<ModelProfile> =
        ResponseEntity.ok(
            profileTransformer.toContract(
                profiles.save(id.toString(), profileTransformer.toDomain(modelProfileUpdate)),
            ),
        )

    override fun deleteModelProfile(id: UUID): ResponseEntity<Ok> {
        profiles.delete(id.toString())
        return ResponseEntity.ok(Ok(true))
    }

    override fun testModelProfile(
        id: UUID,
        modelProfileTestRequest: ModelProfileTestRequest,
    ): ResponseEntity<ModelProfileTestResult> = ResponseEntity.ok(
        profileTransformer.toContract(
            profiles.test(id.toString(), modelProfileTestRequest.confirmGeneration == true),
        ),
    )

    override fun modelRouting(): ResponseEntity<ModelTaskRouting> =
        ResponseEntity.ok(profileTransformer.toContract(profiles.routing()))

    override fun saveModelRouting(modelTaskRouting: ModelTaskRouting): ResponseEntity<ModelTaskRouting> =
        ResponseEntity.ok(
            profileTransformer.toContract(profiles.routing(profileTransformer.toDomain(modelTaskRouting))),
        )

    override fun getModelPrompts(): ResponseEntity<ModelPromptReport> =
        ResponseEntity.ok(promptTransformer.toContract(prompts.list()))

    override fun saveModelPrompts(modelPromptSettings: ModelPromptSettingsInput): ResponseEntity<ModelPromptSettings> =
        ResponseEntity.ok(promptTransformer.toContract(prompts.save(promptTransformer.toDomain(modelPromptSettings))))

    override fun estimatePromptBudget(promptBudgetRequest: PromptBudgetRequest): ResponseEntity<PromptBudgetReport> =
        ResponseEntity.ok(promptTransformer.toContract(prompts.budget(promptTransformer.toDomain(promptBudgetRequest))))

    override fun engineDevices(): ResponseEntity<ComputeDeviceStatus> =
        ResponseEntity.ok(engineTransformer.toContract(engine.devices()))

    override fun preview(preview: Preview): ResponseEntity<Reply> = ResponseEntity.ok(
        previewTransformer.toContract(
            previews.reply(rules.toDomain(preview.rule), preview.chatId.orEmpty(), preview.text),
        ),
    )

    override fun enqueuePreviewJob(previewJobRequest: PreviewJobRequest): ResponseEntity<PreviewJob> =
        ResponseEntity.ok(
            previewTransformer.toContract(
                drafts.enqueue(
                    previewJobRequest.chatId,
                    previewJobRequest.rule?.let(rules::toDomain),
                    previewJobRequest.messageId.orEmpty(),
                ),
            ),
        )

    override fun previewJob(id: String): ResponseEntity<PreviewJob> =
        ResponseEntity.ok(previewTransformer.toContract(drafts.job(id)))

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
