package ru.aigram.exception

import ru.aigram.resources.ResourceCatalog

open class AppException(val code: ErrorCode, parameters: Map<String, Any> = emptyMap(), cause: Throwable? = null) :
    RuntimeException(ResourceCatalog().render("errors/ru/" + code.name.lowercase() + ".txt", parameters), cause) {
    val status: Int = code.status
    override val message: String get() = super.message.orEmpty()
}
