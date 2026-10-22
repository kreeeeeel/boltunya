package ru.aigram.component.system

import org.springframework.stereotype.Component
import ru.aigram.storage.StorageLocations

@Component
class StorageComponent(private val storage: StorageLocations) {
    fun locations() = storage.report()
}
