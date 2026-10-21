package ru.aigram.transformer.models

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.ComputeDevice
import ru.aigram.contract.dto.ComputeDeviceKind
import ru.aigram.contract.dto.ComputeDeviceLimitation
import ru.aigram.contract.dto.ComputeDeviceReason
import ru.aigram.contract.dto.ComputeDeviceStatus
import ru.aigram.domain.ai.ComputeDeviceStatus as DomainComputeDeviceStatus

@Component
class EngineTransformer {
    fun toContract(value: DomainComputeDeviceStatus): ComputeDeviceStatus = ComputeDeviceStatus(
        value.configured,
        value.active,
        value.managed,
        value.selectionSupported,
        value.restartRequired,
        ComputeDeviceLimitation.forValue(value.limitation),
        value.devices.stream().map {
            ComputeDevice(
                it.id,
                it.name,
                ComputeDeviceKind.forValue(it.kind),
                it.memoryBytes,
                it.selectable,
                ComputeDeviceReason.forValue(it.reason),
            )
        }.toList(),
        value.autoUsesEnvironment,
        value.nvidiaSelectionOnly,
    )
}
