package ru.aigram.client.exception

import java.util.ResourceBundle

abstract class AigramClientException(val code: AigramClientErrorCode) :
    RuntimeException(
        ResourceBundle.getBundle("aigram-client-errors").getString(code.name),
    )
