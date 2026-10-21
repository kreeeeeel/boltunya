package ru.aigram.controller.telegram

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.aigram.component.telegram.TelegramComponent
import ru.aigram.contract.api.TelegramApi
import ru.aigram.contract.dto.Credentials
import ru.aigram.contract.dto.Ok
import ru.aigram.contract.dto.Value
import ru.aigram.transformer.telegram.toDomain

@RestController
class TelegramController(private val telegram: TelegramComponent) : TelegramApi {
    override fun connect(credentials: Credentials): ResponseEntity<Ok> {
        telegram.connect(credentials.toDomain())
        return ResponseEntity.ok(Ok(true))
    }

    override fun logout(): ResponseEntity<Ok> {
        telegram.logout()
        return ResponseEntity.ok(Ok(true))
    }

    override fun restoreTelegramSession(): ResponseEntity<Ok> {
        telegram.restore()
        return ResponseEntity.ok(Ok(true))
    }

    override fun submit(value: Value): ResponseEntity<Ok> {
        telegram.submit(value.value)
        return ResponseEntity.ok(Ok(true))
    }
}
