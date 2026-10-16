package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class InvalidFlowNodeException : InvalidInputException(ErrorCode.INVALID_FLOW_NODE)
