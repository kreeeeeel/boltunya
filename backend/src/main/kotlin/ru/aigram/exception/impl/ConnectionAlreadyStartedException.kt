package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class ConnectionAlreadyStartedException : InvalidInputException(ErrorCode.CONNECTION_ALREADY_STARTED)
