package ru.boltunya.http

import jakarta.inject.Inject
import jakarta.validation.Validation
import ru.boltunya.ai.ModelCatalog
import ru.boltunya.exception.impl.ModelNotReadyForAutomationException
import ru.boltunya.exception.impl.UnknownModelException
import ru.boltunya.exception.impl.ValidationFailedException

class InputValidation @Inject constructor(private val models: ModelCatalog) : AutoCloseable {
    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator

    fun validate(value: Any) {
        if (validator.validate(value).isNotEmpty()) throw ValidationFailedException()
    }

    fun model(name: String, forAutomation: Boolean = false) {
        if (!models.contains(name)) throw UnknownModelException()
        if (forAutomation && !models.supportsAutomation(name)) throw ModelNotReadyForAutomationException(name)
    }

    override fun close() {
        factory.close()
    }
}
