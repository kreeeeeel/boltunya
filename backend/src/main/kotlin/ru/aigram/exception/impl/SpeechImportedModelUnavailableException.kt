package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class SpeechImportedModelUnavailableException : InvalidInputException(ErrorCode.SPEECH_IMPORTED_MODEL_UNAVAILABLE)
