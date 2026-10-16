package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class InvalidFlowDefinitionException : InvalidInputException(ErrorCode.INVALID_FLOW_DEFINITION)
