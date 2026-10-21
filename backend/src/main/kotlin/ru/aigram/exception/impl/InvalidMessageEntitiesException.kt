package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class InvalidMessageEntitiesException : InvalidInputException(ErrorCode.INVALID_MESSAGE_ENTITIES)
