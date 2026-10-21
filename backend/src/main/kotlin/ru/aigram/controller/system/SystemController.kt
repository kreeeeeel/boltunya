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
import ru.aigram.contract.dto.MetricSample
import ru.aigram.contract.dto.Ok
import ru.aigram.contract.dto.State
import ru.aigram.contract.dto.StorageLocationReport
import ru.aigram.transformer.system.DiagnosticsTransformer
import ru.aigram.transformer.system.ResourcesTransformer
import ru.aigram.transformer.system.StateTransformer
import ru.aigram.transformer.system.StorageTransformer

@RestController
class SystemController(
    private val state: StateComponent,
    private val diagnostics: DiagnosticsComponent,
    private val resources: ResourcesComponent,
    private val application: ApplicationComponent,
    private val states: StateTransformer,
    private val errors: DiagnosticsTransformer,
    private val locations: StorageTransformer,
    private val resourceValues: ResourcesTransformer,
) : SystemApi {
    override fun state(): ResponseEntity<State> = ResponseEntity.ok(states.toContract(state.get()))

    override fun diagnosticErrors(): ResponseEntity<DiagnosticReport> =
        ResponseEntity.ok(errors.toContract(diagnostics.errors()))

    override fun clearDiagnosticErrors(): ResponseEntity<Ok> {
        diagnostics.clear()
        return ResponseEntity.ok(Ok(true))
    }

    override fun storageLocations(): ResponseEntity<StorageLocationReport> =
        ResponseEntity.ok(locations.toContract(diagnostics.locations()))

    override fun getMetrics(): ResponseEntity<List<MetricSample>> =
        ResponseEntity.ok(resources.metrics().stream().map(resourceValues::toContract).toList())

    override fun getCacheStatistics(): ResponseEntity<CacheStatistics> =
        ResponseEntity.ok(resourceValues.toContract(resources.cache()))

    override fun clearMediaCache(): ResponseEntity<CacheStatistics> =
        ResponseEntity.ok(resourceValues.toContract(resources.clearCache()))

    override fun quit(): ResponseEntity<Ok> {
        application.quit()
        return ResponseEntity.ok(Ok(true))
    }
}
