package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class ChatUnavailableException : InvalidInputException(ErrorCode.CHAT_UNAVAILABLE)
