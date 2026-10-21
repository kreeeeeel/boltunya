package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class MediaNotReadyException : InvalidInputException(ErrorCode.MEDIA_NOT_READY)
