package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ActivityFeedbackUnavailableException : AppException(ErrorCode.ACTIVITY_FEEDBACK_UNAVAILABLE)
