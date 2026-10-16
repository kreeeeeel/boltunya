package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class FlowStorageLimitReachedException : InvalidInputException(ErrorCode.FLOW_STORAGE_LIMIT_REACHED)
