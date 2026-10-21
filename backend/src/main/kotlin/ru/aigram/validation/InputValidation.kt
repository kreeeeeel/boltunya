package ru.aigram.validation

import jakarta.validation.Validator
import org.springframework.stereotype.Component
import ru.aigram.ai.ModelCatalog
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.chat.ChatRule
import ru.aigram.exception.impl.ModelNotReadyForAutomationException
import ru.aigram.exception.impl.UnknownModelException
import ru.aigram.exception.impl.ValidationFailedException

@Component
class InputValidation(
    private val models: ModelCatalog,
    private val targets: ModelTargetResolver,
    private val validator: Validator,
) {
    fun validate(value: Any) {
        if (validator.validate(value).isNotEmpty()) {
            throw ValidationFailedException()
        }
    }

    fun model(name: String, forAutomation: Boolean = false) {
        if (!models.selectable(name)) {
            throw UnknownModelException()
        }

        if (forAutomation && !models.supportsAutomation(name)) {
            throw ModelNotReadyForAutomationException(name)
        }
    }

    fun target(rule: ChatRule, forAutomation: Boolean = false) {
        val target = targets.resolve(ModelTask.REPLY, rule)
        if (target.profileId.isEmpty()) {
            model(target.model, forAutomation)
        }
    }

    fun selection(model: String, profileId: String, forAutomation: Boolean = false) {
        if (profileId.isEmpty()) {
            model(model, forAutomation)
        } else {
            targets.profile(profileId)
        }
    }

    fun localModel(rule: ChatRule): String? {
        val target = targets.resolve(ModelTask.REPLY, rule)
        return if (target.profileId.isEmpty()) {
            target.model
        } else {
            null
        }
    }
}
