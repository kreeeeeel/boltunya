package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class AnimationTooLargeException : InvalidInputException(ErrorCode.ANIMATION_TOO_LARGE)
