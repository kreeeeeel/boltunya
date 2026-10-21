package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class DuplicateStyleIdentifierException : InvalidInputException(ErrorCode.DUPLICATE_STYLE_IDENTIFIER)
