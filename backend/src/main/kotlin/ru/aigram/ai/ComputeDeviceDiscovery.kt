package ru.aigram.ai

import ru.aigram.domain.ComputeDevice

fun interface ComputeDeviceDiscovery {
    fun devices(): List<ComputeDevice>
}
