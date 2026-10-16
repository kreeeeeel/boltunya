package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class FlowIdentifierMismatchException : InvalidInputException(ErrorCode.FLOW_IDENTIFIER_MISMATCH)
