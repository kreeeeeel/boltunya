package ru.aigram.component.speech

import org.springframework.stereotype.Component
import ru.aigram.domain.speech.SpeechComputeReason
import ru.aigram.domain.speech.SpeechComputeStatus
import ru.aigram.speech.SpeechAssetCatalog
import ru.aigram.speech.SpeechComputeProbe
import ru.aigram.storage.SettingsStore
import java.util.concurrent.CancellationException

private const val COMPUTE_RUNTIME_ID = "applio-runtime"

@Component
class SpeechComputeComponent(
    private val settings: SettingsStore,
    private val catalog: SpeechAssetCatalog,
    private val probe: SpeechComputeProbe,
) {
    fun status(): SpeechComputeStatus {
        val selected = settings.get().voiceConversionDevice
        if (catalog.list().stream().noneMatch { it.id == COMPUTE_RUNTIME_ID && it.installed }) {
            return SpeechComputeStatus(selected, false, false, reason = SpeechComputeReason.NOT_INSTALLED)
        }
        try {
            val metadata = probe.probe(catalog.location(COMPUTE_RUNTIME_ID))
            val reason = if (metadata.cudaAvailable) {
                SpeechComputeReason.READY
            } else {
                SpeechComputeReason.CUDA_UNAVAILABLE
            }
            return SpeechComputeStatus(
                selected,
                true,
                metadata.cudaAvailable,
                metadata.gpuName,
                metadata.gpuMemoryBytes,
                metadata.torchVersion,
                metadata.cudaVersion,
                reason,
            )
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw exception
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (Thread.currentThread().isInterrupted) {
                throw exception
            }
            return SpeechComputeStatus(selected, true, false, reason = SpeechComputeReason.PROBE_FAILED)
        }
    }
}
