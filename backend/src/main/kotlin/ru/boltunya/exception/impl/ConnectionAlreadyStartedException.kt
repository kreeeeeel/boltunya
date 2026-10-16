package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class ConnectionAlreadyStartedException : InvalidInputException(ErrorCode.CONNECTION_ALREADY_STARTED)
