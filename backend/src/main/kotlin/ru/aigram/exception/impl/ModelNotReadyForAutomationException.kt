package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class ModelNotReadyForAutomationException(model: String) :
    InvalidInputException(ErrorCode.MODEL_NOT_READY_FOR_AUTOMATION, mapOf("model" to model))
