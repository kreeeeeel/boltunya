package ru.boltunya.http

import jakarta.inject.Inject
import jakarta.validation.Validation
import ru.boltunya.ai.ModelCatalog
import ru.boltunya.ai.profile.ModelTargetResolver
import ru.boltunya.ai.profile.ModelTask
import ru.boltunya.domain.ChatRule
import ru.boltunya.exception.impl.ModelNotReadyForAutomationException
import ru.boltunya.exception.impl.UnknownModelException
import ru.boltunya.exception.impl.ValidationFailedException

class InputValidation @Inject constructor(
    private val models: ModelCatalog,
    private val targets: ModelTargetResolver? = null,
) : AutoCloseable {
    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator

    fun validate(value: Any) {
        if (validator.validate(value).isNotEmpty()) throw ValidationFailedException()
    }

    fun model(name: String, forAutomation: Boolean = false) {
        if (!models.contains(name)) throw UnknownModelException()
        if (forAutomation && !models.supportsAutomation(name)) throw ModelNotReadyForAutomationException(name)
    }

    fun target(rule: ChatRule, forAutomation: Boolean = false) {
        val target = targets?.resolve(ModelTask.REPLY, rule)
        if (target == null || target.profileId.isEmpty()) model(target?.model ?: rule.model, forAutomation)
    }

    fun selection(model: String, profileId: String, forAutomation: Boolean = false) {
        if (profileId.isEmpty()) model(model, forAutomation) else targets?.profile(profileId)
    }

    fun localModel(rule: ChatRule): String? {
        val target = targets?.resolve(ModelTask.REPLY, rule)
        return if (target == null || target.profileId.isEmpty()) target?.model ?: rule.model else null
    }

    override fun close() {
        factory.close()
    }
}
