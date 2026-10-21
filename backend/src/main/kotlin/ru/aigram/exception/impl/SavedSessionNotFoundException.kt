package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class SavedSessionNotFoundException : InvalidInputException(ErrorCode.SAVED_SESSION_NOT_FOUND)
