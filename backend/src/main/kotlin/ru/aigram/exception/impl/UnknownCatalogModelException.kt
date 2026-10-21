package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class UnknownCatalogModelException : InvalidInputException(ErrorCode.UNKNOWN_CATALOG_MODEL)
