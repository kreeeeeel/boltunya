package ru.boltunya.ai

import jakarta.inject.Inject
import ru.boltunya.domain.ComputeDevice
import ru.boltunya.domain.ComputeDeviceStatus
import ru.boltunya.exception.impl.InvalidOllamaDeviceException
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.SettingsStore

private const val AUTOMATIC_DEVICE = "auto"
private const val CPU_DEVICE = "cpu"

class OllamaDeviceSelector @Inject constructor(
    private val settings: SettingsStore,
    private val discovery: ComputeDeviceDiscovery,
    private val resources: ResourceCatalog,
) {
    fun selected(): String = settings.get().ollamaDevice

    fun validate(id: String) {
        if (id == AUTOMATIC_DEVICE || id == CPU_DEVICE) return
        if (id == selected() && resources.regex("common/ollama-device-uuid").matches(id)) return
        if (discovery.devices().stream().noneMatch {
                it.id == id && it.selectable
            }
        ) {
            throw InvalidOllamaDeviceException()
        }
    }

    fun environment(id: String = selected()): Map<String, String> {
        if (id == AUTOMATIC_DEVICE) return emptyMap()
        if (id != CPU_DEVICE && !resources.regex("common/ollama-device-uuid").matches(id)) {
            throw InvalidOllamaDeviceException()
        }
        return mapOf(
            "CUDA_VISIBLE_DEVICES" to if (id == CPU_DEVICE) "-1" else id,
            "HIP_VISIBLE_DEVICES" to "-1",
            "ROCR_VISIBLE_DEVICES" to "-1",
            "GPU_DEVICE_ORDINAL" to "-1",
            "GGML_VK_VISIBLE_DEVICES" to "-1",
            "OLLAMA_VULKAN" to "0",
        )
    }

    fun status(
        online: Boolean,
        local: Boolean,
        defaultLocal: Boolean,
        installed: Boolean,
        managed: Boolean,
        active: String?,
    ): ComputeDeviceStatus {
        val configured = selected()
        val supported = defaultLocal && installed && (!online || managed)
        val pending = managed && active != configured
        val limitation = when {
            !local -> "remote"
            !defaultLocal || online && !managed -> "external"
            !installed -> "missing-runtime"
            pending || !managed -> "next-start"
            else -> ""
        }
        return ComputeDeviceStatus(
            configured,
            if (managed) active else null,
            managed,
            supported,
            pending,
            limitation,
            listOf(
                ComputeDevice(AUTOMATIC_DEVICE, "Auto", "auto"),
                ComputeDevice(CPU_DEVICE, "CPU", "cpu"),
            ) + discovery.devices(),
        )
    }
}
