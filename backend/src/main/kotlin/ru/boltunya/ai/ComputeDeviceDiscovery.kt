package ru.boltunya.ai

import ru.boltunya.domain.ComputeDevice

fun interface ComputeDeviceDiscovery {
    fun devices(): List<ComputeDevice>
}
