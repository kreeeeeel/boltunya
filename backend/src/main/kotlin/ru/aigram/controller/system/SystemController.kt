package ru.aigram.controller.system

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.aigram.component.system.ApplicationComponent
import ru.aigram.component.system.DiagnosticsComponent
import ru.aigram.component.system.ResourcesComponent
import ru.aigram.component.system.StateComponent
import ru.aigram.contract.api.SystemApi
import ru.aigram.contract.dto.CacheStatistics
import ru.aigram.contract.dto.DiagnosticReport
import ru.aigram.contract.dto.Ok
import ru.aigram.contract.dto.State
import ru.aigram.contract.dto.StorageLocationReport
import ru.aigram.transformer.system.toContract

@RestController
class SystemController(
    private val state: StateComponent,
    private val diagnostics: DiagnosticsComponent,
    private val resources: ResourcesComponent,
    private val application: ApplicationComponent,
) : SystemApi {
    override fun state(): ResponseEntity<State> = ResponseEntity.ok(state.get().toContract())

    override fun diagnosticErrors(): ResponseEntity<DiagnosticReport> =
        ResponseEntity.ok(diagnostics.errors().toContract())

    override fun clearDiagnosticErrors(): ResponseEntity<Ok> {
        diagnostics.clear()
        return ResponseEntity.ok(Ok(true))
    }

    override fun storageLocations(): ResponseEntity<StorageLocationReport> =
        ResponseEntity.ok(diagnostics.locations().toContract())

    override fun getCacheStatistics(): ResponseEntity<CacheStatistics> =
        ResponseEntity.ok(resources.cache().toContract())

    override fun clearMediaCache(): ResponseEntity<CacheStatistics> =
        ResponseEntity.ok(resources.clearCache().toContract())

    override fun quit(): ResponseEntity<Ok> {
        application.quit()
        return ResponseEntity.ok(Ok(true))
    }
}
