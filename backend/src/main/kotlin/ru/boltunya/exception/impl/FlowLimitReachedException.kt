package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class FlowLimitReachedException : InvalidInputException(ErrorCode.FLOW_LIMIT_REACHED)
