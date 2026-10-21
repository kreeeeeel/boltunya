package ru.aigram.ai

import ru.aigram.domain.ai.ComputeDevice

fun interface ComputeDeviceDiscovery {
    fun devices(): List<ComputeDevice>
}
