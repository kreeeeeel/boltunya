package ru.boltunya.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import ru.boltunya.resources.ResourceCatalog

class ResourcePatternValidator : ConstraintValidator<ResourcePattern, String> {
    private var name = ""
    private val resources = ResourceCatalog()

    override fun initialize(annotation: ResourcePattern) {
        name = annotation.value
    }

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || resources.regex(name).matches(value)
}
