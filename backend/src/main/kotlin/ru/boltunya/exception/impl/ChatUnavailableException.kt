package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class ChatUnavailableException : InvalidInputException(ErrorCode.CHAT_UNAVAILABLE)
