package ru.aigram.transformer.system

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.StorageLocation
import ru.aigram.contract.dto.StorageLocationKind
import ru.aigram.contract.dto.StorageLocationReport
import ru.aigram.domain.system.storage.StorageLocationReport as DomainStorageLocationReport

@Component
class StorageTransformer {
    fun toContract(value: DomainStorageLocationReport): StorageLocationReport = StorageLocationReport(
        value.locations.stream().map {
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
}
