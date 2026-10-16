package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class FlowNotFoundException : InvalidInputException(ErrorCode.FLOW_NOT_FOUND)
