package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class DuplicateStyleIdentifierException : InvalidInputException(ErrorCode.DUPLICATE_STYLE_IDENTIFIER)
