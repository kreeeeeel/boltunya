package ru.aigram.transformer.system

import ru.aigram.contract.dto.StorageLocation
import ru.aigram.contract.dto.StorageLocationKind
import ru.aigram.contract.dto.StorageLocationReport
import ru.aigram.domain.system.storage.StorageLocationReport as DomainStorageLocationReport

fun DomainStorageLocationReport.toContract(): StorageLocationReport = StorageLocationReport(
    locations.stream().map {
        StorageLocation(
            it.id,
            it.label,
            it.path,
            it.available,
            it.openable,
            StorageLocationKind.forValue(it.kind),
            it.note,
        )
    }.toList(),
)
