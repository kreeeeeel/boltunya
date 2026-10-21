package ru.boltunya.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class InputFormatValidator : ConstraintValidator<InputFormat, String> {
    private lateinit var rule: InputRule

    override fun initialize(annotation: InputFormat) {
        rule = annotation.value
    }

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || rule.accepts(value)
}
