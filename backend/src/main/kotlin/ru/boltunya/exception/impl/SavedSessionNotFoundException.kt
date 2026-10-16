package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class SavedSessionNotFoundException : InvalidInputException(ErrorCode.SAVED_SESSION_NOT_FOUND)
