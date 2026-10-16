package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class UnknownCatalogModelException : InvalidInputException(ErrorCode.UNKNOWN_CATALOG_MODEL)
