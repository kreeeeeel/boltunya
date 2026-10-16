package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class ModelNotReadyForAutomationException(model: String) :
    InvalidInputException(ErrorCode.MODEL_NOT_READY_FOR_AUTOMATION, mapOf("model" to model))
