package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class VisionInputLimitExceededException(imageLimit: Int, fileLimitMb: Long, totalLimitMb: Long) :
    InvalidInputException(
        ErrorCode.VISION_INPUT_LIMIT_EXCEEDED,
        mapOf("images" to imageLimit, "fileMb" to fileLimitMb, "totalMb" to totalLimitMb),
    )
