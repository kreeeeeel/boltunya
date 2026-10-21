package ru.aigram.http

import jakarta.inject.Inject
import jakarta.validation.Validation
import org.springframework.stereotype.Component
import ru.aigram.ai.ModelCatalog
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.ai.profile.ModelTask
import ru.aigram.domain.ChatRule
import ru.aigram.exception.impl.ModelNotReadyForAutomationException
import ru.aigram.exception.impl.UnknownModelException
import ru.aigram.exception.impl.ValidationFailedException
import java.util.concurrent.atomic.AtomicBoolean

@Component
class InputValidation @Inject constructor(
    private val models: ModelCatalog,
    private val targets: ModelTargetResolver? = null,
) : AutoCloseable {
    private val closed = AtomicBoolean()
    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator

    fun validate(value: Any) {
        if (validator.validate(value).isNotEmpty()) throw ValidationFailedException()
    }

    fun model(name: String, forAutomation: Boolean = false) {
        if (!models.selectable(name)) throw UnknownModelException()
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
        if (!closed.compareAndSet(false, true)) {
            return
        }
        factory.close()
    }
}
