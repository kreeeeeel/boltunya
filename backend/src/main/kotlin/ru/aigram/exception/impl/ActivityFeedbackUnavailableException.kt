package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ActivityFeedbackUnavailableException : AppException(ErrorCode.ACTIVITY_FEEDBACK_UNAVAILABLE)
