package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class SpeechInputDisabledException : InvalidInputException(ErrorCode.SPEECH_INPUT_DISABLED)
