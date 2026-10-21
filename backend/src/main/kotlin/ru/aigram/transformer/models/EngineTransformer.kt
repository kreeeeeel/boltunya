package ru.aigram.transformer.models

import ru.aigram.contract.dto.ComputeDevice
import ru.aigram.contract.dto.ComputeDeviceKind
import ru.aigram.contract.dto.ComputeDeviceLimitation
import ru.aigram.contract.dto.ComputeDeviceReason
import ru.aigram.contract.dto.ComputeDeviceStatus
import ru.aigram.domain.ai.ComputeDevice as DomainComputeDevice
import ru.aigram.domain.ai.ComputeDeviceStatus as DomainComputeDeviceStatus

fun DomainComputeDeviceStatus.toContract(): ComputeDeviceStatus = ComputeDeviceStatus(
    configured,
    active,
    managed,
    selectionSupported,
    restartRequired,
    ComputeDeviceLimitation.forValue(limitation),
    devices.stream().map { it.toContract() }.toList(),
    autoUsesEnvironment,
    nvidiaSelectionOnly,
)

fun DomainComputeDevice.toContract(): ComputeDevice = ComputeDevice(
    id,
    name,
    ComputeDeviceKind.forValue(kind),
    memoryBytes,
    selectable,
    ComputeDeviceReason.forValue(reason),
)
