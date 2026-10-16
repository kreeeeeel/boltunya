package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class MediaNotReadyException : InvalidInputException(ErrorCode.MEDIA_NOT_READY)
