package ru.boltunya.http

import jakarta.inject.Inject
import jakarta.validation.Validation
import ru.boltunya.ai.ModelCatalog
import ru.boltunya.exception.impl.UnknownModelException
import ru.boltunya.exception.impl.ValidationFailedException

class InputValidation @Inject constructor(private val models: ModelCatalog) : AutoCloseable {
    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator

    fun validate(value: Any) {
        if (validator.validate(value).isNotEmpty()) throw ValidationFailedException()
    }

    fun model(name: String) {
        if (!models.contains(name)) throw UnknownModelException()
    }

    override fun close() {
        factory.close()
    }
}
